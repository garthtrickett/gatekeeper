package com.aegisgatekeeper.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aegisgatekeeper.app.domain.BlockingRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

class GatekeeperVpnService : VpnService() {
    private val vpnScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var tunInterface: ParcelFileDescriptor? = null
    private var activeBlacklist: Set<String> = emptySet()

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val channelId = "gatekeeper_vpn_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Gatekeeper VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification =
            NotificationCompat
                .Builder(this, channelId)
                .setContentTitle("Gatekeeper VPN Active")
                .setContentText("Surgically filtering network traffic.")
                .setSmallIcon(android.R.drawable.ic_secure)
                .setOngoing(true)
                .build()

        startForeground(2, notification)

        vpnScope.launch {
            GatekeeperStateManager.state.collect { state ->
                val currentApp = state.activeForegroundApp ?: ""
                val blockedDomains = mutableSetOf<String>()

                if (state.isManualLockdownActive) {
                    state.appGroups.forEach { group ->
                        group.rules.filterIsInstance<BlockingRule.DomainBlock>().filter { it.isEnabled }.forEach {
                            blockedDomains.addAll(it.domains)
                        }
                    }
                } else {
                    val activeGroups = state.appGroups.filter { it.apps.contains(currentApp) }
                    activeGroups.forEach { group ->
                        group.rules.filterIsInstance<BlockingRule.DomainBlock>().filter { it.isEnabled }.forEach {
                            blockedDomains.addAll(it.domains)
                        }
                    }
                }
                activeBlacklist = blockedDomains
            }
        }

        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (tunInterface != null) return

        try {
            val builder = Builder()
            builder.addAddress("10.0.0.2", 32)
            builder.addRoute("8.8.8.8", 32)
            builder.addDnsServer("8.8.8.8")
            builder.setSession("Gatekeeper VPN")
            tunInterface = builder.establish()

            tunInterface?.let { tun ->
                vpnScope.launch {
                    processPackets(tun)
                }
            }
        } catch (e: Exception) {
            Log.e("Gatekeeper", "❌ VPN Setup Failed: ${e.message}")
        }
    }

    private suspend fun processPackets(tun: ParcelFileDescriptor) {
        val inputStream = FileInputStream(tun.fileDescriptor)
        val outputStream = FileOutputStream(tun.fileDescriptor)
        val packet = ByteArray(32767)

        while (kotlin.coroutines.coroutineContext.isActive) {
            try {
                val length = inputStream.read(packet)
                if (length > 0) {
                    val buffer = ByteBuffer.wrap(packet, 0, length)
                    handlePacket(buffer, outputStream)
                }
            } catch (e: Exception) {
                Log.e("Gatekeeper", "❌ VPN Read Error: ${e.message}")
                delay(1000)
            }
        }
    }

    private fun handlePacket(
        buffer: ByteBuffer,
        outputStream: FileOutputStream,
    ) {
        // Parse IPv4 Header
        val versionAndIHL = buffer.get(0).toInt()
        val version = versionAndIHL shr 4
        if (version != 4) return // Only IPv4
        val ihl = versionAndIHL and 0x0F
        val ipHeaderLength = ihl * 4

        val protocol = buffer.get(9).toInt() and 0xFF
        if (protocol != 17) return // Only UDP

        val srcIp = buffer.getInt(12)
        val dstIp = buffer.getInt(16)

        // Parse UDP Header
        buffer.position(ipHeaderLength)
        val srcPort = buffer.getShort().toInt() and 0xFFFF
        val dstPort = buffer.getShort().toInt() and 0xFFFF
        val udpLength = buffer.getShort().toInt() and 0xFFFF

        if (dstPort == 53) {
            // DNS Packet
            val dnsPayloadOffset = ipHeaderLength + 8
            val dnsPayloadLength = udpLength - 8
            if (dnsPayloadLength <= 0) return

            val domainName = extractDomainName(buffer.array(), dnsPayloadOffset)

            if (isDomainBlocked(domainName)) {
                Log.d("Gatekeeper", "🛡️ VPN Blocked Domain: $domainName")
                return // Drop packet silently
            }

            // Forward Allowed DNS Request
            forwardDns(buffer.array(), dnsPayloadOffset, dnsPayloadLength, srcIp, srcPort, dstIp, outputStream)
        }
    }

    private fun isDomainBlocked(domain: String): Boolean = activeBlacklist.any { domain.endsWith(it, ignoreCase = true) }

    private fun extractDomainName(
        payload: ByteArray,
        offset: Int,
    ): String {
        var pos = offset + 12 // Skip DNS header (12 bytes)
        val sb = java.lang.StringBuilder()
        while (pos < payload.size) {
            val len = payload[pos].toInt() and 0xFF
            if (len == 0) break
            if (len >= 192) break // Compression pointer
            pos++
            for (i in 0 until len) {
                sb.append(payload[pos].toInt().toChar())
                pos++
            }
            sb.append(".")
        }
        return sb.toString().removeSuffix(".")
    }

    private fun forwardDns(
        dnsPayload: ByteArray,
        offset: Int,
        length: Int,
        originalSrcIp: Int,
        originalSrcPort: Int,
        originalDstIp: Int,
        outputStream: FileOutputStream,
    ) {
        vpnScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                protect(socket)

                val serverAddr =
                    InetAddress.getByAddress(
                        byteArrayOf(
                            (originalDstIp shr 24).toByte(),
                            (originalDstIp shr 16).toByte(),
                            (originalDstIp shr 8).toByte(),
                            originalDstIp.toByte(),
                        ),
                    )

                val requestPacket = DatagramPacket(dnsPayload, offset, length, serverAddr, 53)
                socket.send(requestPacket)

                val responseBuffer = ByteArray(4096)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.soTimeout = 3000
                socket.receive(responsePacket)

                val outPacket =
                    constructUdpIpPacket(
                        srcIp = originalDstIp,
                        dstIp = originalSrcIp,
                        srcPort = 53,
                        dstPort = originalSrcPort,
                        payload = responseBuffer,
                        payloadLength = responsePacket.length,
                    )

                outputStream.write(outPacket)
            } catch (e: Exception) {
                Log.e("Gatekeeper", "❌ VPN DNS Forward Error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    private fun constructUdpIpPacket(
        srcIp: Int,
        dstIp: Int,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
        payloadLength: Int,
    ): ByteArray {
        val totalLength = 20 + 8 + payloadLength
        val buffer = ByteBuffer.allocate(totalLength)

        // IP Header
        buffer.put((0x45).toByte()) // Version(4) and IHL(5)
        buffer.put(0.toByte()) // TOS
        buffer.putShort(totalLength.toShort()) // Total Length
        buffer.putShort(0.toShort()) // Identification
        buffer.putShort(0.toShort()) // Flags & Fragment Offset
        buffer.put(64.toByte()) // TTL
        buffer.put(17.toByte()) // Protocol (UDP)
        buffer.putShort(0.toShort()) // Checksum (calculate later)
        buffer.putInt(srcIp)
        buffer.putInt(dstIp)

        // UDP Header
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort((8 + payloadLength).toShort()) // Length
        buffer.putShort(0.toShort()) // UDP Checksum (0 = disabled for IPv4)

        // Payload
        buffer.put(payload, 0, payloadLength)

        // Calculate IP Checksum
        val ipHeader = buffer.array().copyOfRange(0, 20)
        var sum = 0
        for (i in 0 until 20 step 2) {
            val word = ((ipHeader[i].toInt() and 0xFF) shl 8) + (ipHeader[i + 1].toInt() and 0xFF)
            sum += word
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        buffer.putShort(10, (sum.inv() and 0xFFFF).toShort())

        return buffer.array()
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnScope.cancel()
        tunInterface?.close()
    }
}
