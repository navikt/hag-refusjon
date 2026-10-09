package no.nav.helsearbeidsgiver

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.nav.helsearbeidsgiver.arkiv.ArkivService
import no.nav.helsearbeidsgiver.auth.FakeTokenValidator
import no.nav.helsearbeidsgiver.bucket.FakeBucketStorage
import no.nav.helsearbeidsgiver.kafka.RefusjonProducer
import no.nav.helsearbeidsgiver.kafka.TEST_TOPIC
import no.nav.helsearbeidsgiver.kafka.mockProducer
import no.nav.helsearbeidsgiver.person.PdlService
import no.nav.helsearbeidsgiver.virksomhet.VirksomhetsnavnKlient

class AppTest :
    FunSpec({
        test("GET /hello svarer med Hello World!") {
            testApplication {
                application {
                    module(
                        FakeBucketStorage(),
                        RefusjonProducer(mockProducer(), TEST_TOPIC),
                        mockk<VirksomhetsnavnKlient>(),
                        mockk<PdlService>(),
                        mockk<ArkivService>(),
                        FakeTokenValidator(),
                    )
                }

                val response = client.get("/hello")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "Hello World!"
            }
        }

        test("helsesjekker svarer OK") {
            testApplication {
                application {
                    module(
                        FakeBucketStorage(),
                        RefusjonProducer(mockProducer(), TEST_TOPIC),
                        mockk<VirksomhetsnavnKlient>(),
                        mockk<PdlService>(),
                        mockk<ArkivService>(),
                        FakeTokenValidator(),
                    )
                }

                client.get("/health/is-alive").status shouldBe HttpStatusCode.OK
                client.get("/health/is-ready").status shouldBe HttpStatusCode.OK
            }
        }
    })
