package com.aegisgatekeeper.app.integrations

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.aegisgatekeeper.app.domain.BeeperChat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject

@Inject
class AndroidBeeperClient(
    private val context: Context,
) : BeeperClient {
    override suspend fun getChats(): Either<String, List<BeeperChat>> =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse("content://com.beeper.api/chats?limit=100")
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val chats = mutableListOf<BeeperChat>()

                cursor?.use {
                    val idIndex = it.getColumnIndex("roomId")
                    val nameIndex = it.getColumnIndex("name")
                    val networkIndex = it.getColumnIndex("network")

                    while (it.moveToNext()) {
                        val roomId = if (idIndex >= 0) it.getString(idIndex) else null
                        val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown Chat"
                        val network = if (networkIndex >= 0) it.getString(networkIndex) else null

                        if (roomId != null) {
                            chats.add(BeeperChat(roomId, name, network))
                        }
                    }
                } ?: return@withContext "Failed to query Beeper chats: Cursor was null".left()

                chats.right()
            } catch (e: SecurityException) {
                "Permission denied or Beeper app not installed".left()
            } catch (e: Exception) {
                (e.message ?: "Unknown error querying Beeper").left()
            }
        }

    override suspend fun sendMessage(
        roomId: String,
        text: String,
    ): Either<String, Unit> =
        withContext(Dispatchers.IO) {
            try {
                val messageUri = Uri.parse("content://com.beeper.api/messages?roomId=${Uri.encode(roomId)}&text=${Uri.encode(text)}")
                val resultUri = context.contentResolver.insert(messageUri, ContentValues())

                if (resultUri != null) {
                    Unit.right()
                } else {
                    "Failed to send message: Provider returned null".left()
                }
            } catch (e: SecurityException) {
                "Permission denied or Beeper app not installed".left()
            } catch (e: Exception) {
                (e.message ?: "Unknown error sending message via Beeper").left()
            }
        }
}
