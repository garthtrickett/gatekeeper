package com.aegisgatekeeper.backend

import com.aegisgatekeeper.backend.db.DatabaseFactory
import com.aegisgatekeeper.backend.routes.authRouting
import com.aegisgatekeeper.backend.routes.syncRouting
import com.google.firebase.FirebaseApp
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(
        factory = CIO, 
        port = 8081, 
        host = "0.0.0.0", 
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    configureSecurity()

    try {
        FirebaseApp.initializeApp()
    } catch (e: Exception) {
        environment.log.error("Failed to initialize FirebaseApp (Credentials may be missing)", e)
    }

    install(ContentNegotiation) {
        json()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, "500: ${cause.message}")
        }
    }

    routing {
        get("/") {
            call.respondText("Hello from Gatekeeper Sovereign Sync HQ!")
        }
        authRouting()
        syncRouting()
    }
}
