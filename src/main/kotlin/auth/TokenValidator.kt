package no.nav.helsearbeidsgiver.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import no.nav.helsearbeidsgiver.utils.createHttpClient
import org.slf4j.LoggerFactory

interface TokenValidator {
    /** Returnerer tokenets claims hvis tokenet er gyldig, ellers null. */
    suspend fun valider(token: String): JsonObject?
}

// Inneholder alle claims fra tokenet, slik at f.eks. azp kan sjekkes senere.
data class TokenPrincipal(
    val claims: JsonObject,
)

// Validerer Entra ID-tokens med Texas sitt introspection-endepunkt: https://docs.nais.io/auth/entra-id/how-to/secure/#validate-tokens
class TexasTokenValidator(
    private val introspectionEndpoint: String,
    private val httpClient: HttpClient = createHttpClient(),
) : TokenValidator {
    private val logger = LoggerFactory.getLogger(TexasTokenValidator::class.java)

    override suspend fun valider(token: String): JsonObject? {
        val respons =
            httpClient
                .submitForm(
                    url = introspectionEndpoint,
                    formParameters =
                        parameters {
                            append("identity_provider", "entra_id")
                            append("token", token)
                        },
                ).body<JsonObject>()

        return if (respons["active"]?.jsonPrimitive?.booleanOrNull == true) {
            respons
        } else {
            logger.info("Ugyldig token: ${respons["error"]?.jsonPrimitive?.contentOrNull}")
            null
        }
    }
}
