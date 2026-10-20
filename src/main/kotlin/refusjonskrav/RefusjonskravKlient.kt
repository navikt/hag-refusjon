@file:UseSerializers(UuidSerializer::class)

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
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.utils.createHttpClient
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.util.UUID

class RefusjonskravKlient(
    private val baseUrl: String,
    private val getAccessToken: () -> String,
    private val client: HttpClient = createHttpClient(),
) {
    suspend fun hentRefusjonskrav(inntektsmeldingIder: List<UUID>): List<InntektsmeldingRefusjonskrav> =
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
    val inntektsmeldingIder: List<UUID>,
)

@Serializable
data class InntektsmeldingRefusjonskrav(
    val inntektsmeldingId: UUID,
    val refusjonskrav: Refusjon?,
)
