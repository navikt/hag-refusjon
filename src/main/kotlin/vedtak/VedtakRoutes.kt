package no.nav.helsearbeidsgiver.vedtak

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("VedtakRoutes")

fun Route.vedtakRoutes() {
    post("/arbeidstaker-vedtak") {
        val melding =
            try {
                call.receive<ArbeidstakerVedtakMelding>()
            } catch (e: BadRequestException) {
                logger.warn("Kunne ikke lese melding om arbeidstakervedtak.")
                call.respond(HttpStatusCode.BadRequest, "Ugyldig melding")
                return@post
            }

        logger.info("Mottok arbeidstakervedtak for vedtaksperiodeId ${melding.vedtaksperiodeId}.")
        call.respond(HttpStatusCode.OK)
    }
}
