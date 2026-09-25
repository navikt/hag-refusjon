package no.nav.helsearbeidsgiver.helsesjekker

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.naisRoutes() {
    route("/health") {
        get("/is-alive") {
            call.respond(HttpStatusCode.OK, "Alive")
        }
        get("/is-ready") {
            call.respond(HttpStatusCode.OK, "Ready")
        }
    }
}
