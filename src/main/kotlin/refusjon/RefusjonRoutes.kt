package no.nav.helsearbeidsgiver.refusjon

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.helsearbeidsgiver.bucket.BucketStorage
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.respondMedPDF
import no.nav.helsearbeidsgiver.utils.toUuidOrNull
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RefusjonRoutes")

fun Route.vedtakRoutes(vedtakMottak: VedtakMottak) {
    // midlertidlig mottak POST route fra LPS API (bytter til å motta direkte fra SAS i fremtiden)
    post("/arbeidstaker-vedtak") {
        val melding =
            try {
                call.receive<ArbeidstakerVedtakMelding>()
            } catch (e: BadRequestException) {
                sikkerLogger().warn("Kunne ikke lese melding om arbeidstakervedtak.", e)
                call.respond(HttpStatusCode.BadRequest, "Ugyldig melding")
                return@post
            }

        sikkerLogger().info(
            "Mottok arbeidstakervedtak med behandlingId ${melding.behandlingId} og vedtaksperiodeId ${melding.vedtaksperiodeId}:\n" +
                melding.toJson(ArbeidstakerVedtakMelding.serializer()).toPretty(),
        )

        if (vedtakMottak.motta(melding)) {
            call.respond(HttpStatusCode.Created)
        } else {
            sikkerLogger().info("Arbeidstakervedtak med behandlingId ${melding.behandlingId} var allerede lagret.")
            call.respond(HttpStatusCode.Conflict, "Vedtaket er allerede mottatt")
        }
    }
}

fun Route.pdfRoutes(bucketStorage: BucketStorage) {
    get("/refusjonsutfall/{refusjonsutfallId}/pdf") {
        val refusjonsutfallId = call.parameters["refusjonsutfallId"]?.toUuidOrNull()
        if (refusjonsutfallId == null) {
            call.respond(HttpStatusCode.BadRequest, "Ugyldig refusjonsutfallId")
            return@get
        }

        val pdf =
            try {
                bucketStorage.hentPdf(refusjonsutfallId)
            } catch (e: Exception) {
                "Feil ved henting av PDF for refusjonsutfall med refusjonsutfallId $refusjonsutfallId."
                    .also {
                        logger.error(it)
                        sikkerLogger().error(it, e)
                    }
                call.respond(HttpStatusCode.InternalServerError, "Feil ved henting av refusjonsutfall")
                return@get
            }

        if (pdf == null) {
            call.respond(HttpStatusCode.NotFound, "Fant ikke refusjonsutfall")
            return@get
        }

        call.respondMedPDF(bytes = pdf, filnavn = "refusjonsutfall-$refusjonsutfallId.pdf")
    }
}
