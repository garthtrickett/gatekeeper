package com.aegisgatekeeper.backend.routes

import com.aegisgatekeeper.app.sync.ContentItemDto
import com.aegisgatekeeper.app.sync.SyncPullPayload
import com.aegisgatekeeper.app.sync.SyncPushPayload
import com.aegisgatekeeper.app.sync.VaultItemDto
import com.aegisgatekeeper.backend.db.DatabaseFactory
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Routing.syncRouting() {
    authenticate("auth-jwt") {
        route("/sync") {
            post("/register-device") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()
                val payload = call.receive<com.aegisgatekeeper.app.sync.DeviceRegistrationRequest>()
                
                val queries = DatabaseFactory.db.gatekeeperServerDatabaseQueries
                queries.insertDeviceToken(userId = userId, fcmToken = payload.fcmToken)
                
                call.respond(mapOf("status" to "ok"))
            }

            post("/push") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()
                val payload = call.receive<SyncPushPayload>()
                val queries = DatabaseFactory.db.gatekeeperServerDatabaseQueries

                DatabaseFactory.db.transaction {
                    // Upsert Vault items
                    payload.vaultItems.forEach { item ->
                        val existing = queries.getVaultItem(item.id, userId).executeAsOneOrNull()
                        if (existing == null) {
                            queries.insertVaultItem(
                                id = item.id,
                                userId = userId,
                                query = item.query,
                                capturedAtTimestamp = item.capturedAtTimestamp,
                                isResolved = item.isResolved,
                                lastModified = item.lastModified,
                                isDeleted = item.isDeleted
                            )
                        } else if (existing.lastModified < item.lastModified) {
                            queries.updateVaultItem(
                                id = item.id,
                                userId = userId,
                                query = item.query,
                                capturedAtTimestamp = item.capturedAtTimestamp,
                                isResolved = item.isResolved,
                                lastModified = item.lastModified,
                                isDeleted = item.isDeleted
                            )
                        }
                    }

                    // Upsert Content items
                    payload.contentItems.forEach { item ->
                        val existing = queries.getContentItem(item.id, userId).executeAsOneOrNull()
                        if (existing == null) {
                            queries.insertContentItem(
                                id = item.id,
                                userId = userId,
                                videoId = item.videoId,
                                title = item.title,
                                channelName = item.channelName,
                                source = item.source,
                                type = item.type,
                                rank = item.rank,
                                capturedAtTimestamp = item.capturedAtTimestamp,
                                durationSeconds = item.durationSeconds,
                                lastModified = item.lastModified,
                                isDeleted = item.isDeleted
                            )
                        } else if (existing.lastModified < item.lastModified) {
                            queries.updateContentItem(
                                id = item.id,
                                userId = userId,
                                videoId = item.videoId,
                                title = item.title,
                                channelName = item.channelName,
                                source = item.source,
                                type = item.type,
                                rank = item.rank,
                                capturedAtTimestamp = item.capturedAtTimestamp,
                                durationSeconds = item.durationSeconds,
                                lastModified = item.lastModified,
                                isDeleted = item.isDeleted
                            )
                        }
                    }
                }

                // Send FCM Poke to all registered devices for this user
                val tokens = queries.getTokensForUser(userId).executeAsList()
                if (tokens.isNotEmpty()) {
                    try {
                        val message = com.google.firebase.messaging.MulticastMessage.builder()
                            .putData("action", "sync_poke")
                            .addAllTokens(tokens)
                            .build()
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().sendMulticastAsync(message)
                    } catch (e: Exception) {
                        call.application.environment.log.error("Failed to send FCM pokes", e)
                    }
                }

                call.respond(mapOf("status" to "ok"))
            }

            get("/pull") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim("userId").asString()
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                val queries = DatabaseFactory.db.gatekeeperServerDatabaseQueries

                val vaultItems = queries.getVaultItemsSince(userId, since).executeAsList().map { row ->
                    VaultItemDto(
                        id = row.id,
                        query = row.query,
                        capturedAtTimestamp = row.capturedAtTimestamp,
                        isResolved = row.isResolved ?: false,
                        lastModified = row.lastModified,
                        isDeleted = row.isDeleted ?: false
                    )
                }

                val contentItems = queries.getContentItemsSince(userId, since).executeAsList().map { row ->
                    ContentItemDto(
                        id = row.id,
                        videoId = row.videoId,
                        title = row.title,
                        channelName = row.channelName,
                        source = row.source,
                        type = row.type,
                        rank = row.rank,
                        capturedAtTimestamp = row.capturedAtTimestamp,
                        durationSeconds = row.durationSeconds,
                        lastModified = row.lastModified,
                        isDeleted = row.isDeleted ?: false
                    )
                }

                call.respond(
                    SyncPullPayload(
                        vaultItems = vaultItems,
                        contentItems = contentItems,
                        serverTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
