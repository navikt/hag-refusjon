package no.nav.helsearbeidsgiver.utils

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.RoutingCall
import no.nav.helsearbeidsgiver.Env.getPropertyOrNull
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import no.nav.helsearbeidsgiver.vedtak.ArbeidstakerVedtakMelding

object PdfgenHttpClient {
    val httpClient = createHttpClient()
    val PDFGEN_REFUSJON_URL = getPropertyOrNull("PDFGEN_REFUSJON_URL").orDefault { throw RuntimeException("PDFGEN_REFUSJON_URL ikke satt") }
}

suspend fun genererRefusjonPdf(vedtak: ArbeidstakerVedtakMelding) = hentPdf(vedtak, PdfgenHttpClient.PDFGEN_REFUSJON_URL)

private suspend fun hentPdf(
    body: Any?,
    url: String,
): ByteArray {
    val response =
        PdfgenHttpClient.httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    if (response.status != HttpStatusCode.OK) {
        "En feil oppstod ved generering av PDF med pdfgen til $url: ${response.status}\nBody: ${response.bodyAsText()}"
            .also { sikkerLogger().error(it) }
        throw RuntimeException("Intern feil oppstod ved generering av PDF")
    }
    return response.readRawBytes()
}

suspend fun RoutingCall.respondMedPDF(
    bytes: ByteArray,
    filnavn: String,
) {
    this.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"$filnavn\"")
    this.respondBytes(
        bytes = bytes,
        contentType = ContentType.Application.Pdf,
        status = HttpStatusCode.OK,
    )
}
