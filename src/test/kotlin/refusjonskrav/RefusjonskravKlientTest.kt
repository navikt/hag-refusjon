package no.nav.helsearbeidsgiver.refusjonskrav

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.json.parseJson
import java.util.UUID
import kotlin.uuid.toKotlinUuid

private const val BASE_URL = "http://spinosaurus"
private val ID_1 = UUID.randomUUID()
private val ID_2 = UUID.randomUUID()

class RefusjonskravKlientTest :
    FunSpec({
        test("sender POST med JSON-body, UUID-er og bearer-token") {
            mockSpinosaurus(
                forventedeIder = listOf(ID_2, ID_1, ID_2),
                responsBody = "[]",
            ).use { client ->
                val klient = SpinosaurusRefusjonskravKlient(BASE_URL, { "et-token" }, client)

                klient.hentRefusjonskrav(listOf(ID_2, ID_1, ID_2)) shouldBe emptyList()
            }
        }

        test("bevarer responsrekkefolgen og null for inntektsmeldinger uten refusjon") {
            mockSpinosaurus(
                forventedeIder = listOf(ID_2, ID_1, ID_2),
                responsBody =
                    """
                    [
                        {"inntektsmeldingId": "$ID_2", "refusjonskrav": null},
                        {"inntektsmeldingId": "$ID_1", "refusjonskrav": null}
                    ]
                    """.trimIndent(),
            ).use { client ->
                val klient = SpinosaurusRefusjonskravKlient(BASE_URL, { "et-token" }, client)

                klient.hentRefusjonskrav(listOf(ID_2, ID_1, ID_2)) shouldBe
                    listOf(
                        InntektsmeldingRefusjonskrav(ID_2, null),
                        InntektsmeldingRefusjonskrav(ID_1, null),
                    )
            }
        }

        test("legger ikke til inntektsmeldinger som er utelatt fra responsen") {
            mockSpinosaurus(
                forventedeIder = listOf(ID_1, ID_2),
                responsBody = """[{"inntektsmeldingId": "$ID_2", "refusjonskrav": null}]""",
            ).use { client ->
                val klient = SpinosaurusRefusjonskravKlient(BASE_URL, { "et-token" }, client)

                klient.hentRefusjonskrav(listOf(ID_1, ID_2)) shouldBe
                    listOf(InntektsmeldingRefusjonskrav(ID_2, null))
            }
        }

        test("sender en tom liste som JSON-body") {
            mockSpinosaurus(emptyList(), "[]").use { client ->
                val klient = SpinosaurusRefusjonskravKlient(BASE_URL, { "et-token" }, client)

                klient.hentRefusjonskrav(emptyList()) shouldBe emptyList()
            }
        }

        test("propagerer 400 fra Spinosaurus") {
            mockSpinosaurus(listOf(ID_1), """{"message": "Ugyldig request"}""", HttpStatusCode.BadRequest).use { client ->
                val klient = SpinosaurusRefusjonskravKlient(BASE_URL, { "et-token" }, client)

                shouldThrow<ClientRequestException> {
                    klient.hentRefusjonskrav(listOf(ID_1))
                }.response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("propagerer 500 fra Spinosaurus") {
            mockSpinosaurus(listOf(ID_1), """{"message": "Intern serverfeil"}""", HttpStatusCode.InternalServerError).use { client ->
                val klient = SpinosaurusRefusjonskravKlient(BASE_URL, { "et-token" }, client)

                shouldThrow<ServerResponseException> {
                    klient.hentRefusjonskrav(listOf(ID_1))
                }.response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    })

private fun mockSpinosaurus(
    forventedeIder: List<UUID>,
    responsBody: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpClient =
    HttpClient(
        MockEngine { request ->
            request.method shouldBe HttpMethod.Post
            request.url.toString() shouldBe "$BASE_URL/api/v1/inntektsmelding/refusjonskrav"
            request.headers[HttpHeaders.Accept] shouldBe ContentType.Application.Json.toString()
            request.headers[HttpHeaders.Authorization] shouldBe "Bearer et-token"
            val body = request.body as TextContent
            body.contentType.withoutParameters() shouldBe ContentType.Application.Json
            body.text.parseJson() shouldBe
                """{"inntektsmeldingIder": [${forventedeIder.joinToString { "\"$it\"" }}]}""".parseJson()

            respond(
                content = responsBody,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        },
    ) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(jsonConfig)
        }
    }
