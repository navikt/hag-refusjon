package no.nav.helsearbeidsgiver.refusjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helsearbeidsgiver.auth.FakeTokenValidator
import no.nav.helsearbeidsgiver.auth.GYLDIG_TOKEN
import no.nav.helsearbeidsgiver.bucket.FakeBucketStorage
import no.nav.helsearbeidsgiver.module
import java.util.UUID
import kotlin.uuid.toKotlinUuid

class VedtakRoutesTest :
    FunSpec({
        val pdfBytes = "PDF innhold".toByteArray()

        test("POST /arbeidstaker-vedtak med gyldig melding oppretter bakgrunnsjobb og svarer Created") {
            val vedtakMottak = mockk<VedtakMottak> { every { motta(any()) } returns true }

            testApplication {
                application { module(FakeBucketStorage(), FakeTokenValidator(), vedtakMottak) }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody(gyldigMelding(FNR, ORGNR))
                    }

                response.status shouldBe HttpStatusCode.Created
                verify(exactly = 1) { vedtakMottak.motta(match { it.foedselsnummer == FNR && it.organisasjonsnummer == ORGNR }) }
            }
        }

        test("POST /arbeidstaker-vedtak svarer Conflict når vedtaket allerede er mottatt") {
            val vedtakMottak = mockk<VedtakMottak> { every { motta(any()) } returns false }

            testApplication {
                application { module(FakeBucketStorage(), FakeTokenValidator(), vedtakMottak) }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody(gyldigMelding(FNR, ORGNR))
                    }

                response.status shouldBe HttpStatusCode.Conflict
            }
        }

        test("POST /arbeidstaker-vedtak med ugyldig melding svarer Bad Request") {
            val vedtakMottak = mockk<VedtakMottak>()

            testApplication {
                application { module(FakeBucketStorage(), FakeTokenValidator(), vedtakMottak) }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"eventName": "vedtak_fattet"}""")
                    }

                response.status shouldBe HttpStatusCode.BadRequest
                verify(exactly = 0) { vedtakMottak.motta(any()) }
            }
        }

        test("POST /arbeidstaker-vedtak svarer Internal Server Error når lagring feiler") {
            val vedtakMottak = mockk<VedtakMottak> { every { motta(any()) } throws RuntimeException("db nede") }

            testApplication {
                application { module(FakeBucketStorage(), FakeTokenValidator(), vedtakMottak) }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody(gyldigMelding(FNR, ORGNR))
                    }

                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }

        test("GET /refusjonsutfall/{refusjonsutfallId}/pdf svarer med PDF fra bucket") {
            val refusjonsutfallId = UUID.randomUUID().toKotlinUuid()
            val bucketStorage = FakeBucketStorage().apply { lagrePdf(refusjonsutfallId, pdfBytes) }

            testApplication {
                application { module(bucketStorage, FakeTokenValidator(), mockk<VedtakMottak>()) }

                val response = client.get("/refusjonsutfall/$refusjonsutfallId/pdf") { bearerAuth(GYLDIG_TOKEN) }

                response.status shouldBe HttpStatusCode.OK
                response.headers[HttpHeaders.ContentType] shouldBe ContentType.Application.Pdf.toString()
                response.headers[HttpHeaders.ContentDisposition] shouldBe "inline; filename=\"refusjonsutfall-$refusjonsutfallId.pdf\""
                response.bodyAsBytes() shouldBe pdfBytes
            }
        }

        test("GET /refusjonsutfall/{refusjonsutfallId}/pdf svarer Not Found når PDF ikke finnes i bucket") {
            testApplication {
                application { module(FakeBucketStorage(), FakeTokenValidator(), mockk<VedtakMottak>()) }

                val response = client.get("/refusjonsutfall/${UUID.randomUUID()}/pdf") { bearerAuth(GYLDIG_TOKEN) }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        test("GET /refusjonsutfall/{refusjonsutfallId}/pdf med ugyldig refusjonsutfallId svarer Bad Request") {
            testApplication {
                application { module(FakeBucketStorage(), FakeTokenValidator(), mockk<VedtakMottak>()) }

                val response = client.get("/refusjonsutfall/ikke-en-uuid/pdf") { bearerAuth(GYLDIG_TOKEN) }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        val lagretId = UUID.randomUUID().toKotlinUuid()
        listOf(
            "hex uten bindestreker" to lagretId.toHexString(),
            "for kort id" to lagretId.toString().dropLast(1),
            "for lang id" to "${lagretId}0",
            "ikke-hex-tegn i riktig format" to "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz",
            "korte segmenter" to "1-1-1-1-1",
        ).forEach { (beskrivelse, ugyldigId) ->
            test("GET /refusjonsutfall/{refusjonsutfallId}/pdf svarer Bad Request for refusjonsutfallId med $beskrivelse") {
                val bucketStorage = FakeBucketStorage().apply { lagrePdf(lagretId, pdfBytes) }

                testApplication {
                    application { module(bucketStorage, FakeTokenValidator(), mockk<VedtakMottak>()) }

                    val response = client.get("/refusjonsutfall/$ugyldigId/pdf") { bearerAuth(GYLDIG_TOKEN) }

                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }

        test("GET /refusjonsutfall/{refusjonsutfallId}/pdf med ugyldig token svarer Unauthorized") {
            val refusjonsutfallId = UUID.randomUUID().toKotlinUuid()
            val bucketStorage = FakeBucketStorage().apply { lagrePdf(refusjonsutfallId, pdfBytes) }

            testApplication {
                application { module(bucketStorage, FakeTokenValidator(), mockk<VedtakMottak>()) }

                val response = client.get("/refusjonsutfall/$refusjonsutfallId/pdf") { bearerAuth("ugyldig-token") }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("GET /refusjonsutfall/{refusjonsutfallId}/pdf uten token svarer Unauthorized") {
            val refusjonsutfallId = UUID.randomUUID().toKotlinUuid()
            val bucketStorage = FakeBucketStorage().apply { lagrePdf(refusjonsutfallId, pdfBytes) }

            testApplication {
                application { module(bucketStorage, FakeTokenValidator(), mockk<VedtakMottak>()) }

                val response = client.get("/refusjonsutfall/$refusjonsutfallId/pdf")

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
    })
