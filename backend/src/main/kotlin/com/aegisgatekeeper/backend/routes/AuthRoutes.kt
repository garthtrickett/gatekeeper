package com.aegisgatekeeper.backend.routes

import com.aegisgatekeeper.backend.JwtConfig
import com.aegisgatekeeper.backend.db.DatabaseFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class MagicLinkRequest(val email: String)

fun Routing.authRouting() {
    route("/auth") {
        if (System.getenv("DEV_MODE") == "true") {
            get("/dev-token") {
                val userId = "dev-e2e-user"
                val token = JwtConfig.generateToken(userId)
                call.respond(HttpStatusCode.OK, mapOf("token" to token))
            }
        }

        post("/magic-link") {
            val request = call.receive<MagicLinkRequest>()
            val email = request.email.lowercase().trim()

            // Find or create user
            val queries = DatabaseFactory.db.gatekeeperServerDatabaseQueries
            var userId = queries.findUserByEmail(email).executeAsOneOrNull()?.id
            
            if (userId == null) {
                userId = UUID.randomUUID().toString()
                queries.insertUser(id = userId, email = email)
            }

            // Generate short-lived token for the magic link
            val magicToken = JwtConfig.generateToken(userId, isMagicLink = true)

            // In a real app, we would use an email service here.
            // For now, we'll just log it. This is a critical security step.
            call.application.environment.log.info("---- MAGIC LINK FOR ${email} ----")
            call.application.environment.log.info("http://localhost:8081/auth/verify-token?token=${magicToken}")
            call.application.environment.log.info("---------------------------------")


            call.respond(HttpStatusCode.OK, mapOf("message" to "Magic link sent. Check your inbox."))
        }

        get("/verify-token") {
            val token = call.request.queryParameters["token"]
            if (token == null) {
                call.respond(HttpStatusCode.BadRequest, "Token is missing.")
                return@get
            }

            try {
                val decodedJWT = JwtConfig.verifier.verify(token)
                val userId = decodedJWT.getClaim("userId").asString()

                // The token is valid, issue a long-lived JWT
                val longLivedToken = JwtConfig.generateToken(userId)
                call.respond(HttpStatusCode.OK, mapOf("token" to longLivedToken))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or expired token.")
            }
        }
    }
}
