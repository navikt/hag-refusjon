package no.nav.helsearbeidsgiver.vedtak

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import no.nav.helsearbeidsgiver.arkiv.ArkivService
import no.nav.helsearbeidsgiver.auth.FakeTokenValidator
import no.nav.helsearbeidsgiver.auth.GYLDIG_TOKEN
import no.nav.helsearbeidsgiver.bucket.FakeBucketStorage
import no.nav.helsearbeidsgiver.kafka.RefusjonProducer
import no.nav.helsearbeidsgiver.kafka.TEST_TOPIC
import no.nav.helsearbeidsgiver.kafka.mockProducer
import no.nav.helsearbeidsgiver.module
import no.nav.helsearbeidsgiver.person.PdlService
import no.nav.helsearbeidsgiver.utils.PdfgenHttpClient
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.toUuidOrNull
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import no.nav.helsearbeidsgiver.virksomhet.VirksomhetsnavnKlient
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val ARBEIDSGIVER_NAVN = "Billys Bollefabrikk AS"
private const val SYKMELDT_NAVN = "Ola Nordmann"
private const val JOURNALPOST_ID = "123456789"
private val ORGNR = Orgnr.genererGyldig()
private val FNR = Fnr.genererGyldig()

class VedtakRoutesTest :
    FunSpec({
        val pdfBytes = "PDF innhold".toByteArray()

        afterEach { unmockkAll() }

        test("POST /arbeidstaker-vedtak med gyldig melding genererer PDF, lagrer den i bucket og svarer med refusjonsutfallId") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            val arkivService = arkivServiceMock()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            testApplication {
                application {
                    module(
                        bucketStorage,
                        RefusjonProducer(mockProducer, TEST_TOPIC),
                        virksomhetsnavnKlientMock(),
                        pdlServiceMock(),
                        arkivService,
                        FakeTokenValidator(),
                    )
                }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody(gyldigMelding(FNR, ORGNR))
                    }

                response.status shouldBe HttpStatusCode.OK

                val refusjonsutfallId = response.bodyAsText().toUuidOrNull()
                refusjonsutfallId shouldNotBe null
                bucketStorage.pdfer[refusjonsutfallId] shouldBe pdfBytes

                coVerify(exactly = 1) { arkivService.arkiver(any(), refusjonsutfallId!!, ARBEIDSGIVER_NAVN, pdfBytes) }

                val sendt = mockProducer.history()
                sendt shouldHaveSize 1
                sendt.first().key() shouldBe "c62594af-f0b8-4fd1-88f2-07e1b15dd906"
                sendt
                    .first()
                    .value()
                    .parseJson()
                    .fromJson(RefusjonUtfall.serializer()) shouldBe
                    RefusjonUtfall(
                        refusjonUtfallId = refusjonsutfallId!!,
                        vedtaksperiodeId = UUID.fromString("c62594af-f0b8-4fd1-88f2-07e1b15dd906"),
                        fnr = FNR,
                        orgnr = ORGNR,
                        fom = LocalDate.of(2026, 7, 28),
                        tom = LocalDate.of(2026, 8, 3),
                        sykepengegrunnlag = 154999.92,
                        utfallTilArbeidsgiver = Utfall.INNVILGELSE,
                        fattetTidspunkt = LocalDateTime.parse("2026-08-05T13:03:25.166498222"),
                        journalpostId = JOURNALPOST_ID,
                        arbeidsgiverNavn = ARBEIDSGIVER_NAVN,
                        sykmeldtNavn = SYKMELDT_NAVN,
                    )
            }
        }

        test("POST /arbeidstaker-vedtak svarer Internal Server Error når publisering til Kafka feiler") {
            val mockProducer = mockProducer(autoComplete = false).apply { sendException = RuntimeException("kafka nede") }
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            testApplication {
                application {
                    module(
                        FakeBucketStorage(),
                        RefusjonProducer(mockProducer, TEST_TOPIC),
                        virksomhetsnavnKlientMock(),
                        pdlServiceMock(),
                        arkivServiceMock(),
                        FakeTokenValidator(),
                    )
                }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody(gyldigMelding(FNR, ORGNR))
                    }

                response.status shouldBe HttpStatusCode.InternalServerError
                mockProducer.history().shouldBeEmpty()
            }
        }

        test("POST /arbeidstaker-vedtak svarer Internal Server Error og publiserer ingenting når arkivering feiler") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)
            val arkivService =
                mockk<ArkivService> {
                    coEvery { arkiver(any(), any(), any(), any()) } throws RuntimeException("dokarkiv nede")
                }

            testApplication {
                application {
                    module(
                        bucketStorage,
                        RefusjonProducer(mockProducer, TEST_TOPIC),
                        virksomhetsnavnKlientMock(),
                        pdlServiceMock(),
                        arkivService,
                        FakeTokenValidator(),
                    )
                }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody(gyldigMelding(FNR, ORGNR))
                    }

                response.status shouldBe HttpStatusCode.InternalServerError
                bucketStorage.pdfer.size shouldBe 1
                mockProducer.history().shouldBeEmpty()
            }
        }

        test("POST /arbeidstaker-vedtak med ugyldig melding svarer Bad Request") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()

            testApplication {
                application {
                    module(
                        bucketStorage,
                        RefusjonProducer(mockProducer, TEST_TOPIC),
                        virksomhetsnavnKlientMock(),
                        pdlServiceMock(),
                        arkivServiceMock(),
                        FakeTokenValidator(),
                    )
                }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"eventName": "vedtak_fattet"}""")
                    }

                response.status shouldBe HttpStatusCode.BadRequest
                bucketStorage.pdfer.size shouldBe 0
                mockProducer.history().shouldBeEmpty()
            }
        }

        test("POST /arbeidstaker-vedtak svarer Internal Server Error og lagrer ingenting når pdfgen feiler") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            mockPdfgen(HttpStatusCode.InternalServerError, "Error".toByteArray())

            testApplication {
                application {
                    module(
                        bucketStorage,
                        RefusjonProducer(mockProducer, TEST_TOPIC),
                        virksomhetsnavnKlientMock(),
                        pdlServiceMock(),
                        arkivServiceMock(),
                        FakeTokenValidator(),
                    )
                }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody(gyldigMelding(FNR, ORGNR))
                    }

                response.status shouldBe HttpStatusCode.InternalServerError
                bucketStorage.pdfer.size shouldBe 0
                mockProducer.history().shouldBeEmpty()
            }
        }

        test("POST /arbeidstaker-vedtak svarer Internal Server Error og gjør ingenting mer når virksomhetsnavn ikke finnes") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            testApplication {
                application {
                    module(
                        bucketStorage,
                        RefusjonProducer(mockProducer, TEST_TOPIC),
                        virksomhetsnavnKlientMock(navn = null),
                        pdlServiceMock(),
                        arkivServiceMock(),
                        FakeTokenValidator(),
                    )
                }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody(gyldigMelding(FNR, ORGNR))
                    }

                response.status shouldBe HttpStatusCode.InternalServerError
                bucketStorage.pdfer.size shouldBe 0
                mockProducer.history().shouldBeEmpty()
            }
        }

        test("POST /arbeidstaker-vedtak svarer Internal Server Error og gjør ingenting mer når henting av virksomhetsnavn feiler") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)
            val klient =
                mockk<VirksomhetsnavnKlient> {
                    coEvery { hentVirksomhetsnavn(ORGNR) } throws RuntimeException("brreg nede")
                }

            testApplication {
                application {
                    module(
                        bucketStorage,
                        RefusjonProducer(mockProducer, TEST_TOPIC),
                        klient,
                        pdlServiceMock(),
                        arkivServiceMock(),
                        FakeTokenValidator(),
                    )
                }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody(gyldigMelding(FNR, ORGNR))
                    }

                response.status shouldBe HttpStatusCode.InternalServerError
                bucketStorage.pdfer.size shouldBe 0
                mockProducer.history().shouldBeEmpty()
            }
        }

        test("POST /arbeidstaker-vedtak svarer Internal Server Error og gjør ingenting mer når sykmeldtnavn ikke finnes") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            testApplication {
                application {
                    module(
                        bucketStorage,
                        RefusjonProducer(mockProducer, TEST_TOPIC),
                        virksomhetsnavnKlientMock(),
                        pdlServiceMock(navn = null),
                        arkivServiceMock(),
                        FakeTokenValidator(),
                    )
                }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody(gyldigMelding(FNR, ORGNR))
                    }

                response.status shouldBe HttpStatusCode.InternalServerError
                bucketStorage.pdfer.size shouldBe 0
                mockProducer.history().shouldBeEmpty()
            }
        }

        test("POST /arbeidstaker-vedtak svarer Internal Server Error og gjør ingenting mer når henting av sykmeldtnavn feiler") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)
            val klient =
                mockk<PdlService> {
                    coEvery { hentSykmeldtnavn(FNR) } throws RuntimeException("pdl nede")
                }

            testApplication {
                application {
                    module(
                        bucketStorage,
                        RefusjonProducer(mockProducer, TEST_TOPIC),
                        virksomhetsnavnKlientMock(),
                        klient,
                        arkivServiceMock(),
                        FakeTokenValidator(),
                    )
                }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody(gyldigMelding(FNR, ORGNR))
                    }

                response.status shouldBe HttpStatusCode.InternalServerError
                bucketStorage.pdfer.size shouldBe 0
                mockProducer.history().shouldBeEmpty()
            }
        }

        test("GET /refusjonsutfall/{refusjonsutfallId}/pdf svarer med PDF fra bucket") {
            val refusjonsutfallId = UUID.randomUUID()
            val bucketStorage = FakeBucketStorage().apply { lagrePdf(refusjonsutfallId, pdfBytes) }

            testApplication {
                application {
                    module(
                        bucketStorage,
                        RefusjonProducer(mockProducer(), TEST_TOPIC),
                        virksomhetsnavnKlientMock(),
                        pdlServiceMock(),
                        arkivServiceMock(),
                        FakeTokenValidator(),
                    )
                }

                val response = client.get("/refusjonsutfall/$refusjonsutfallId/pdf") { bearerAuth(GYLDIG_TOKEN) }

                response.status shouldBe HttpStatusCode.OK
                response.headers[HttpHeaders.ContentType] shouldBe ContentType.Application.Pdf.toString()
                response.headers[HttpHeaders.ContentDisposition] shouldBe "inline; filename=\"refusjonsutfall-$refusjonsutfallId.pdf\""
                response.bodyAsBytes() shouldBe pdfBytes
            }
        }

        test("GET /refusjonsutfall/{refusjonsutfallId}/pdf svarer Not Found når PDF ikke finnes i bucket") {
            testApplication {
                application {
                    module(
                        FakeBucketStorage(),
                        RefusjonProducer(mockProducer(), TEST_TOPIC),
                        virksomhetsnavnKlientMock(),
                        pdlServiceMock(),
                        arkivServiceMock(),
                        FakeTokenValidator(),
                    )
                }

                val response = client.get("/refusjonsutfall/${UUID.randomUUID()}/pdf") { bearerAuth(GYLDIG_TOKEN) }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        test("GET /refusjonsutfall/{refusjonsutfallId}/pdf med ugyldig refusjonsutfallId svarer Bad Request") {
            testApplication {
                application {
                    module(
                        FakeBucketStorage(),
                        RefusjonProducer(mockProducer(), TEST_TOPIC),
                        virksomhetsnavnKlientMock(),
                        pdlServiceMock(),
                        arkivServiceMock(),
                        FakeTokenValidator(),
                    )
                }

                val response = client.get("/refusjonsutfall/ikke-en-uuid/pdf") { bearerAuth(GYLDIG_TOKEN) }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("GET /refusjonsutfall/{refusjonsutfallId}/pdf med ugyldig token svarer Unauthorized") {
            val refusjonsutfallId = UUID.randomUUID()
            val bucketStorage = FakeBucketStorage().apply { lagrePdf(refusjonsutfallId, pdfBytes) }

            testApplication {
                application {
                    module(
                        bucketStorage,
                        RefusjonProducer(mockProducer(), TEST_TOPIC),
                        virksomhetsnavnKlientMock(),
                        pdlServiceMock(),
                        arkivServiceMock(),
                        FakeTokenValidator(),
                    )
                }

                val response = client.get("/refusjonsutfall/$refusjonsutfallId/pdf") { bearerAuth("ugyldig-token") }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("GET /refusjonsutfall/{refusjonsutfallId}/pdf uten token svarer Unauthorized") {
            val refusjonsutfallId = UUID.randomUUID()
            val bucketStorage = FakeBucketStorage().apply { lagrePdf(refusjonsutfallId, pdfBytes) }

            testApplication {
                application {
                    module(
                        bucketStorage,
                        RefusjonProducer(mockProducer(), TEST_TOPIC),
                        virksomhetsnavnKlientMock(),
                        pdlServiceMock(),
                        arkivServiceMock(),
                        FakeTokenValidator(),
                    )
                }

                val response = client.get("/refusjonsutfall/$refusjonsutfallId/pdf")

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
    })

private fun virksomhetsnavnKlientMock(navn: String? = ARBEIDSGIVER_NAVN): VirksomhetsnavnKlient =
    mockk {
        coEvery { hentVirksomhetsnavn(ORGNR) } returns navn
    }

private fun pdlServiceMock(navn: String? = SYKMELDT_NAVN): PdlService =
    mockk {
        coEvery { hentSykmeldtnavn(FNR) } returns navn
    }

private fun arkivServiceMock(): ArkivService =
    mockk {
        coEvery { arkiver(any(), any(), any(), any()) } returns JOURNALPOST_ID
    }

private fun mockPdfgen(
    status: HttpStatusCode,
    content: ByteArray,
) {
    val mockEngine =
        MockEngine { request ->
            request.url.toString() shouldBe PdfgenHttpClient.PDFGEN_REFUSJON_URL

            respond(
                content = ByteReadChannel(content),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Pdf.toString()),
            )
        }

    val mockHttpClient =
        HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }
    mockkObject(PdfgenHttpClient)
    every { PdfgenHttpClient.httpClient } returns mockHttpClient
}

private fun gyldigMelding(
    fnr: Fnr,
    orgnr: Orgnr,
): String =
    """
    {
      "eventName": "vedtak_fattet",
      "fødselsnummer": "$fnr",
      "organisasjonsnummer": "$orgnr",
      "yrkesaktivitetstype": "ARBEIDSTAKER",
      "vedtaksperiodeId": "c62594af-f0b8-4fd1-88f2-07e1b15dd906",
      "fom": "2026-07-28",
      "tom": "2026-08-03",
      "skjæringstidspunkt": "2026-07-01",
      "dokumenter": [
        {
          "dokumentId": "be3ec904-4dd6-3bbd-89a9-945b23b5f1be",
          "type": "Søknad"
        }
      ],
      "sykepengegrunnlag": 154999.92,
      "utbetalingsdager": [
        {
          "dato": "2026-07-28",
          "type": "NavDag",
          "beløpTilArbeidsgiver": 1431
        }
      ],
      "vedtakFattetTidspunkt": "2026-08-05T13:03:25.166498222",
      "vedtaksUtfallTilArbeidsgiver": "INNVILGELSE",
      "saksbehandlerIdent": "L1337",
      "saksbehandlerNavn": "Leif Saksbehandler Saksbehandlersen",
      "beslutterIdent": "M1337",
      "beslutterNavn": "Mons Beslutter Besluttersen",
      "automatiskFattet": false,
      "harArbeidsgiverØnsketRefusjon": true
    }
    """.trimIndent()
