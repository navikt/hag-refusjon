package no.nav.helsearbeidsgiver.vedtak

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.helsearbeidsgiver.bucket.BucketStorage
import no.nav.helsearbeidsgiver.kafka.RefusjonProducer
import no.nav.helsearbeidsgiver.utils.genererRefusjonPdf
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.respondMedPDF
import no.nav.helsearbeidsgiver.utils.toUuidOrNull
import no.nav.helsearbeidsgiver.virksomhet.VirksomhetsnavnKlient
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("RefusjonRoutes")

fun Route.refusjonRoutes(
    bucketStorage: BucketStorage,
    refusjonProducer: RefusjonProducer,
    virksomhetsnavnKlient: VirksomhetsnavnKlient,
) {
    // midlertidlig mottak POST route fra LPS API (bytter til å motta direkte fra SAS i fremtiden)
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

        val refusjonUtfallId = UUID.randomUUID()

        val arbeidsgiverNavn =
            try {
                virksomhetsnavnKlient.hentVirksomhetsnavn(melding.organisasjonsnummer)
                    ?: throw IllegalStateException("Fant ikke virksomhetsnavn.")
            } catch (e: Exception) {
                "Feil ved henting av virksomhetsnavn for refusjonsutfall med refusjonUtfallId $refusjonUtfallId og vedtaksperiodeId ${melding.vedtaksperiodeId}."
                    .also {
                        logger.error(it)
                        sikkerLogger().error(it, e)
                    }
                call.respond(HttpStatusCode.InternalServerError, "Feil ved henting av virksomhetsnavn")
                return@post
            }

        try {
            val pdf = genererRefusjonPdf(melding.tilRefusjonPdfData(arbeidsgiverNavn))
            bucketStorage.lagrePdf(refusjonUtfallId, pdf)
        } catch (e: Exception) {
            "Feil ved generering eller lagring av PDF for refusjonsutfall med refusjonUtfallId $refusjonUtfallId og vedtaksperiodeId ${melding.vedtaksperiodeId}."
                .also {
                    logger.error(it)
                    sikkerLogger().error(it, e)
                }
            call.respond(HttpStatusCode.InternalServerError, "Feil ved lagring av refusjonsutfall")
            return@post
        }

        logger.info(
            "Lagret PDF for refusjonsutfall med refusjonsutfallId $refusjonUtfallId og vedtaksperiodeId ${melding.vedtaksperiodeId}.",
        )

        try {
            refusjonProducer.send(melding.vedtaksperiodeId, melding.tilRefusjonUtfall(refusjonUtfallId, arbeidsgiverNavn))
        } catch (e: Exception) {
            "Feil ved publisering av refusjonsutfall med refusjonsutfallId $refusjonUtfallId og vedtaksperiodeId ${melding.vedtaksperiodeId}."
                .also {
                    logger.error(it)
                    sikkerLogger().error(it, e)
                }
            call.respond(HttpStatusCode.InternalServerError, "Feil ved publisering av refusjonsutfall")
            return@post
        }

        logger.info(
            "Publiserte refusjonsutfall med refusjonsutfallId $refusjonUtfallId og vedtaksperiodeId ${melding.vedtaksperiodeId}.",
        )

        call.respond(HttpStatusCode.OK, refusjonUtfallId.toString())
    }

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
