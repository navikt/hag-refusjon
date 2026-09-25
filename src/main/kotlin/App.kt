package no.nav.helsearbeidsgiver

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.helsearbeidsgiver.helsesjekker.naisRoutes
import org.slf4j.LoggerFactory

fun main() {
    LoggerFactory.getLogger("App").info("Starter server...")
    embeddedServer(
        factory = Netty,
        port = 8080,
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    routing {
        naisRoutes()
        get("/hello") {
            call.respondText("Hello World!")
        }
    }
}
