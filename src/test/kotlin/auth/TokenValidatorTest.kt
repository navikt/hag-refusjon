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

class TokenValidatorTest :
    FunSpec({
        test("returnerer claims når tokenet er gyldig") {
            val responsBody = """{"active": true, "azp": "klient-id", "exp": 1730980893}"""

            val claims = TexasTokenValidator(INTROSPECTION_ENDPOINT, mockTexas(responsBody)).valider("et-token")

            claims shouldBe responsBody.parseJson()
        }

        test("returnerer null når tokenet er ugyldig") {
            val responsBody = """{"active": false, "error": "token is expired"}"""

            val claims = TexasTokenValidator(INTROSPECTION_ENDPOINT, mockTexas(responsBody)).valider("et-token")

            claims shouldBe null
        }
    })

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
