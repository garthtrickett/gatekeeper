package com.aegisgatekeeper.backend

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import java.util.Date
import java.util.concurrent.TimeUnit

object JwtConfig {
    private val secret = System.getenv("JWT_SECRET") ?: "default-secret-for-development"
    private val issuer = "gatekeeper-hq"
    private val audience = "gatekeeper-clients"
    val realm = "Gatekeeper Sovereign Sync"

    private val algorithm = Algorithm.HMAC256(secret)
    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withAudience(audience)
        .withIssuer(issuer)
        .build()

    fun generateToken(userId: String, isMagicLink: Boolean = false): String {
        val validityInMs = if (isMagicLink) {
            TimeUnit.MINUTES.toMillis(15) // Magic links are short-lived
        } else {
            TimeUnit.DAYS.toMillis(365) // Client tokens are long-lived
        }

        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
            .sign(algorithm)
    }
}

fun Application.configureSecurity() {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = JwtConfig.realm
            verifier(JwtConfig.verifier)
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}
