package no.nav.helsearbeidsgiver.auth

import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.createHttpClient

class AzureAdTokenHenter(
    private val tokenEndpoint: String,
) {
    private val httpClient = createHttpClient()

    fun tokenGetter(target: String): () -> String = { runBlocking { hentToken(target) } }

    private suspend fun hentToken(target: String): String =
        httpClient
            .submitForm(
                url = tokenEndpoint,
                formParameters =
                    parameters {
                        append("identity_provider", "azuread")
                        append("target", target)
                    },
            ).body<TokenResponse>()
            .accessToken
}

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
)
