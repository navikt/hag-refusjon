package no.nav.helsearbeidsgiver

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.helsearbeidsgiver.auth.FakeTokenValidator
import no.nav.helsearbeidsgiver.bucket.FakeBucketStorage

class AppTest :
    FunSpec({

        test("helsesjekker svarer OK") {
            testApplication {
                application {
                    module(
                        FakeBucketStorage(),
                        FakeTokenValidator(),
                    )
                }

                client.get("/health/is-alive").status shouldBe HttpStatusCode.OK
                client.get("/health/is-ready").status shouldBe HttpStatusCode.OK
            }
        }
    })
