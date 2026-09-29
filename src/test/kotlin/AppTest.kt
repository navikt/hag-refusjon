package no.nav.helsearbeidsgiver

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.helsearbeidsgiver.bucket.MockBucketStorage

class AppTest :
    FunSpec({
        test("GET /hello svarer med Hello World!") {
            testApplication {
                application { module(MockBucketStorage()) }

                val response = client.get("/hello")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "Hello World!"
            }
        }

        test("helsesjekker svarer OK") {
            testApplication {
                application { module(MockBucketStorage()) }

                client.get("/health/is-alive").status shouldBe HttpStatusCode.OK
                client.get("/health/is-ready").status shouldBe HttpStatusCode.OK
            }
        }
    })
