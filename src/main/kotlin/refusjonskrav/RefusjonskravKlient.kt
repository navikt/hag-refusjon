package no.nav.helsearbeidsgiver.refusjonskrav

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.utils.createHttpClient
import kotlin.uuid.Uuid

class RefusjonskravKlient(
    private val baseUrl: String,
    private val getAccessToken: () -> String,
    private val client: HttpClient = createHttpClient(),
) {
    suspend fun hentRefusjonskrav(inntektsmeldingIder: List<Uuid>): List<InntektsmeldingRefusjonskrav> =
        client
            .post(baseUrl + "/api/v1/inntektsmelding/refusjonskrav") {
                contentType(ContentType.Application.Json)
                setBody(RefusjonskravRequest(inntektsmeldingIder))
                accept(ContentType.Application.Json)
                bearerAuth(getAccessToken())
            }.body<List<InntektsmeldingRefusjonskrav>>()
}

@Serializable
private data class RefusjonskravRequest(
    val inntektsmeldingIder: List<Uuid>,
)

@Serializable
data class InntektsmeldingRefusjonskrav(
    val inntektsmeldingId: Uuid,
    val refusjonskrav: Refusjon?,
)
