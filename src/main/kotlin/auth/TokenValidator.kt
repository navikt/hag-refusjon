package no.nav.helsearbeidsgiver.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.helsearbeidsgiver.utils.createHttpClient
import no.nav.helsearbeidsgiver.utils.json.parseJson
import org.slf4j.LoggerFactory

interface TokenValidator {
    /** Returnerer tokenets claims hvis tokenet er gyldig, ellers null. */
    suspend fun valider(token: String): JsonObject?
}

data class TokenPrincipal(
    val claims: JsonObject,
)

// Validerer Entra ID-tokens med Texas sitt introspection-endepunkt: https://docs.nais.io/auth/entra-id/how-to/secure/#validate-tokens
class TexasTokenValidator(
    private val introspectionEndpoint: String,
    private val preAutoriserteKlientIder: Set<String>,
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

        if (respons["active"]?.jsonPrimitive?.booleanOrNull != true) {
            logger.info("Ugyldig token: ${respons["error"]?.jsonPrimitive?.contentOrNull}")
            return null
        }

        val azp = respons["azp"]?.jsonPrimitive?.contentOrNull
        if (azp !in preAutoriserteKlientIder) {
            logger.info("Ukjent azp: ${respons["azp_name"]?.jsonPrimitive?.contentOrNull} ($azp)")
            return null
        }

        return respons
    }
}

// Leser clientId-ene fra AZURE_APP_PRE_AUTHORIZED_APPS, f.eks. [{"name":"dev-gcp:team:app","clientId":"..."}]
fun parsePreAutoriserteKlientIder(json: String): Set<String> =
    json
        .parseJson()
        .jsonArray
        .map { it.jsonObject.getValue("clientId").jsonPrimitive.content }
        .toSet()
