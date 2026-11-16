package no.nav.helsearbeidsgiver.refusjon

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.helsearbeidsgiver.bucket.BucketStorage
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.respondMedPDF
import no.nav.helsearbeidsgiver.utils.toUuidOrNull
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RefusjonRoutes")

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
