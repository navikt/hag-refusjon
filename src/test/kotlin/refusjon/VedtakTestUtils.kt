package no.nav.helsearbeidsgiver.refusjon

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import no.nav.helsearbeidsgiver.arkiv.ArkivService
import no.nav.helsearbeidsgiver.person.PdlService
import no.nav.helsearbeidsgiver.utils.PdfgenHttpClient
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import no.nav.helsearbeidsgiver.virksomhet.VirksomhetsnavnKlient
import kotlin.uuid.Uuid

const val ARBEIDSGIVER_NAVN = "Billys Bollefabrikk AS"
const val SYKMELDT_NAVN = "Ola Nordmann"
const val JOURNALPOST_ID = "123456789"
val ORGNR = Orgnr.genererGyldig()
val FNR = Fnr.genererGyldig()

fun virksomhetsnavnKlientMock(navn: String? = ARBEIDSGIVER_NAVN): VirksomhetsnavnKlient =
    mockk {
        coEvery { hentVirksomhetsnavn(ORGNR) } returns navn
    }

fun pdlServiceMock(navn: String? = SYKMELDT_NAVN): PdlService =
    mockk {
        coEvery { hentSykmeldtnavn(FNR) } returns navn
    }

fun arkivServiceMock(): ArkivService =
    mockk {
        coEvery { arkiver(any(), any()) } returns JOURNALPOST_ID
    }

fun mockPdfgen(
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

fun gyldigMelding(
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
      "behandlingId": "5f1c3a1e-7b0e-4d3b-9a43-3c2f1f0d9a11",
      "fom": "2026-07-28",
      "tom": "2026-08-03",
      "skjæringstidspunkt": "2026-07-01",
      "dokumenter": [
        {
          "dokumentId": "a762f7c3-db3a-4f4b-872b-d37642ea69ba",
          "type": "Søknad"
        }
      ],
      "sykepengegrunnlag": 154999.92,
      "anvendtSeksG": 819294.0,
      "utbetalingsdager": [
        {
          "dato": "2026-07-28",
          "type": "NavDag",
          "sykdomsgrad": 100,
          "dekningsgrad": 100,
          "beløpTilBruker": 0,
          "beløpTilArbeidsgiver": 1431,
          "forespurtRefusjon": {
            "beløp": 1431,
            "avsender": "ARBEIDSGIVER",
            "sasInntektsmeldingReferanse": null,
            "hagInntektsmeldingReferanse": "65f84fa6-554e-4200-9466-30fc7e1ffb2e"
          },
          "begrunnelser": []
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

fun utfallRepositoryMock(): RefusjonUtfallRepository {
    val lagret = mutableMapOf<Uuid, RefusjonUtfall>()
    return mockk {
        every { hent(any()) } answers { lagret[firstArg()] }
        every { lagre(any(), any()) } answers { lagret.putIfAbsent(firstArg(), secondArg()) == null }
    }
}
