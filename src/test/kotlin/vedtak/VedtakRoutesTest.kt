package no.nav.helsearbeidsgiver.vedtak

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.helsearbeidsgiver.module
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr

class VedtakRoutesTest :
    FunSpec({
        test("POST /arbeidstaker-vedtak med gyldig melding svarer OK") {
            testApplication {
                application { module() }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody(gyldigMelding(Fnr.genererGyldig().verdi))
                    }

                response.status shouldBe HttpStatusCode.OK
            }
        }

        test("POST /arbeidstaker-vedtak med ugyldig melding svarer Bad Request") {
            testApplication {
                application { module() }

                val response =
                    client.post("/arbeidstaker-vedtak") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"eventName": "vedtak_fattet"}""")
                    }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }
    })

private fun gyldigMelding(fnr: String): String =
    """
    {
      "eventName": "vedtak_fattet",
      "fødselsnummer": "$fnr",
      "organisasjonsnummer": "896929119",
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
