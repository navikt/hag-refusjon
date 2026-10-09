package no.nav.helsearbeidsgiver.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.json.parseJson

private const val INTROSPECTION_ENDPOINT = "http://texas/api/v1/introspect"
private const val KJENT_KLIENT_ID = "kjent-klient-id"

class TokenValidatorTest :
    FunSpec({
        test("returnerer claims når tokenet er gyldig og azp er preautorisert") {
            val responsBody = """{"active": true, "azp": "$KJENT_KLIENT_ID", "exp": 1730980893}"""

            val claims = validator(responsBody).valider("et-token")

            claims shouldBe responsBody.parseJson()
        }

        test("returnerer null når tokenet er ugyldig") {
            val responsBody = """{"active": false, "error": "token is expired"}"""

            val claims = validator(responsBody).valider("et-token")

            claims shouldBe null
        }

        test("returnerer null når azp ikke er preautorisert") {
            val responsBody = """{"active": true, "azp": "ukjent-klient-id", "azp_name": "dev-gcp:team:ukjent-app"}"""

            val claims = validator(responsBody).valider("et-token")

            claims shouldBe null
        }

        test("returnerer null når azp mangler") {
            val responsBody = """{"active": true}"""

            val claims = validator(responsBody).valider("et-token")

            claims shouldBe null
        }

        test("parsePreAutoriserteKlientIder henter ut clientId-ene") {
            val json =
                """
                [
                  {"name": "dev-gcp:helsearbeidsgiver:sykepenger-im-lps-api", "clientId": "klient-id-1"},
                  {"name": "dev-gcp:team:annen-app", "clientId": "klient-id-2"}
                ]
                """.trimIndent()

            parsePreAutoriserteKlientIder(json) shouldBe setOf("klient-id-1", "klient-id-2")
        }
    })

private fun validator(responsBody: String): TexasTokenValidator =
    TexasTokenValidator(INTROSPECTION_ENDPOINT, setOf(KJENT_KLIENT_ID), mockTexas(responsBody))

private fun mockTexas(responsBody: String): HttpClient =
    HttpClient(
        MockEngine { request ->
            request.url.toString() shouldBe INTROSPECTION_ENDPOINT
            val form = (request.body as FormDataContent).formData
            form["identity_provider"] shouldBe "entra_id"
            form["token"] shouldBe "et-token"

            respond(
                content = responsBody,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        },
    ) {
        install(ContentNegotiation) {
            json(jsonConfig)
        }
    }
