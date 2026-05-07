package com.aegisgatekeeper.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder

class WebViewMediaService : Service() {
    private var mediaSession: MediaSession? = null
    private var currentTitle: String = "Gatekeeper Media"
    private var isPlaying: Boolean = false
    private var openIntentKey: String? = null
    private var openIntentValue: String? = null

    override fun onCreate() {
        super.onCreate()
        val channelId = "webview_media_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Web Media Player", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        mediaSession =
            MediaSession(this, "WebViewMediaSession").apply {
                isActive = true
                setCallback(
                    object : MediaSession.Callback() {
                        override fun onPlay() {
                            sendBroadcast(Intent("com.aegisgatekeeper.app.WEB_PLAY").setPackage(packageName))
                        }

                        override fun onPause() {
                            sendBroadcast(Intent("com.aegisgatekeeper.app.WEB_PAUSE").setPackage(packageName))
                        }

                        override fun onStop() {
                            sendBroadcast(Intent("com.aegisgatekeeper.app.WEB_STOP").setPackage(packageName))
                        }
                    },
                )
            }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            "com.aegisgatekeeper.app.SERVICE_START" -> {
                currentTitle = intent.getStringExtra("EXTRA_TITLE") ?: "Gatekeeper Media"
                openIntentKey = intent.getStringExtra("EXTRA_OPEN_INTENT_KEY")
                openIntentValue = intent.getStringExtra("EXTRA_OPEN_INTENT_VALUE")
                updateNotification()
            }

            "com.aegisgatekeeper.app.SERVICE_UPDATE" -> {
                isPlaying = intent.getBooleanExtra("EXTRA_IS_PLAYING", false)
                updateNotification()
            }

            "com.aegisgatekeeper.app.SERVICE_STOP" -> {
                val launchIntent =
                    Intent(this, com.aegisgatekeeper.app.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                startActivity(launchIntent)
                sendBroadcast(Intent("com.aegisgatekeeper.app.WEB_STOP"))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun updateNotification() {
        val playbackState = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        mediaSession?.setPlaybackState(
            PlaybackState
                .Builder()
                .setState(playbackState, 0L, 1f)
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
                .build(),
        )

        mediaSession?.setMetadata(
            MediaMetadata
                .Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Aegis Gatekeeper")
                .build(),
        )

        val playIntent =
            PendingIntent.getBroadcast(
                this,
                0,
                Intent("com.aegisgatekeeper.app.WEB_PLAY").setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val pauseIntent =
            PendingIntent.getBroadcast(
                this,
                1,
                Intent("com.aegisgatekeeper.app.WEB_PAUSE").setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            PendingIntent.getBroadcast(
                this,
                2,
                Intent("com.aegisgatekeeper.app.WEB_STOP").setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val contentIntent =
            PendingIntent.getActivity(
                this,
                openIntentValue?.hashCode() ?: 1001,
                Intent(this, com.aegisgatekeeper.app.MainActivity::class.java).apply {
                    action = "com.aegisgatekeeper.app.OPEN_WEB_PLAYER_${openIntentValue ?: "unknown"}"
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    if (openIntentKey != null && openIntentValue != null) {
                        putExtra(openIntentKey, openIntentValue)
                    }
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            Notification
                .Builder(this, "webview_media_channel")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(currentTitle)
                .setContentText("Aegis Gatekeeper")
                .setContentIntent(contentIntent)
                .setStyle(Notification.MediaStyle().setMediaSession(mediaSession?.sessionToken).setShowActionsInCompactView(0, 1))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying)

        val playIcon =
            android.graphics.drawable.Icon
                .createWithResource(this, android.R.drawable.ic_media_play)
        val pauseIcon =
            android.graphics.drawable.Icon
                .createWithResource(this, android.R.drawable.ic_media_pause)
        val stopIcon =
            android.graphics.drawable.Icon
                .createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel)

        if (isPlaying) {
            builder.addAction(Notification.Action.Builder(pauseIcon, "Pause", pauseIntent).build())
        } else {
            builder.addAction(Notification.Action.Builder(playIcon, "Play", playIntent).build())
        }
        builder.addAction(Notification.Action.Builder(stopIcon, "Stop", stopIntent).build())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1005,
                builder.build(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(1005, builder.build())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.isActive = false
        mediaSession?.release()
    }
}
