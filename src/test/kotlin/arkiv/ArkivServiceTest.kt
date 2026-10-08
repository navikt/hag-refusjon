package no.nav.helsearbeidsgiver.arkiv

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.Avsender
import no.nav.helsearbeidsgiver.dokarkiv.domene.Dokument
import no.nav.helsearbeidsgiver.dokarkiv.domene.DokumentVariant
import no.nav.helsearbeidsgiver.dokarkiv.domene.GjelderPerson
import no.nav.helsearbeidsgiver.dokarkiv.domene.Journalposttype
import no.nav.helsearbeidsgiver.dokarkiv.domene.Kanal
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import no.nav.helsearbeidsgiver.vedtak.ArbeidstakerVedtakMelding
import no.nav.helsearbeidsgiver.vedtak.SisEventName
import no.nav.helsearbeidsgiver.vedtak.VedtaksUtfall
import no.nav.helsearbeidsgiver.vedtak.Yrkesaktivitetstype
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

class ArkivServiceTest :
    FunSpec({
        test("arkiverer PDF som utgående journalpost med ARKIV-variant og returnerer journalpostId") {
            val fnr = Fnr.genererGyldig()
            val orgnr = Orgnr.genererGyldig()
            val refusjonUtfallId = UUID.randomUUID()
            val pdf = "PDF innhold".toByteArray()

            val avsender = slot<Avsender>()
            val dokumenter = slot<List<Dokument>>()
            val eksternReferanseId = slot<String>()
            val kanal = slot<Kanal>()

            val dokArkivClient =
                mockk<DokArkivClient> {
                    coEvery {
                        opprettOgFerdigstillJournalpostUtgaaende(
                            tittel = "Refusjon av sykepenger til arbeidsgiver",
                            gjelderPerson = GjelderPerson(fnr.verdi),
                            avsender = capture(avsender),
                            dokumenter = capture(dokumenter),
                            eksternReferanseId = capture(eksternReferanseId),
                            callId = any(),
                            kanal = capture(kanal),
                            overstyrInnsynsregler = null,
                        )
                    } returns
                        OpprettOgFerdigstillResponse(
                            journalpostId = "123456789",
                            journalpostFerdigstilt = true,
                            dokumenter = emptyList(),
                        )
                }

            val journalpostId =
                ArkivService(dokArkivClient).arkiver(
                    melding = melding(fnr, orgnr),
                    refusjonUtfallId = refusjonUtfallId,
                    arbeidsgiverNavn = "Billys Bollefabrikk AS",
                    pdf = pdf,
                )

            journalpostId shouldBe "123456789"
            avsender.captured shouldBe Avsender.Organisasjon(orgnr = orgnr.verdi, navn = "Billys Bollefabrikk AS")
            eksternReferanseId.captured shouldBe refusjonUtfallId.toString()
            kanal.captured shouldBe Kanal.ALTINN
            dokumenter.captured shouldBe
                listOf(
                    Dokument(
                        tittel = "Refusjon av sykepenger til arbeidsgiver",
                        brevkode = "REFUSJON_SYKEPENGER",
                        dokumentVarianter =
                            listOf(
                                DokumentVariant(
                                    filtype = "PDFA",
                                    fysiskDokument = Base64.getEncoder().encodeToString(pdf),
                                    variantFormat = "ARKIV",
                                    filnavn = "refusjonsutfall-$refusjonUtfallId.pdf",
                                ),
                            ),
                    ),
                )
        }
    })

private fun melding(
    fnr: Fnr,
    orgnr: Orgnr,
): ArbeidstakerVedtakMelding =
    ArbeidstakerVedtakMelding(
        eventName = SisEventName.VEDTAK_FATTET,
        foedselsnummer = fnr,
        yrkesaktivitetstype = Yrkesaktivitetstype.ARBEIDSTAKER,
        organisasjonsnummer = orgnr,
        vedtaksperiodeId = UUID.randomUUID(),
        fom = LocalDate.of(2026, 7, 28),
        tom = LocalDate.of(2026, 8, 3),
        skjaeringstidspunkt = LocalDate.of(2026, 7, 1),
        dokumenter = emptyList(),
        sykepengegrunnlag = 154999.92,
        vedtakFattetTidspunkt = LocalDateTime.of(2026, 8, 5, 13, 3, 25),
        vedtaksUtfallTilArbeidsgiver = VedtaksUtfall.INNVILGELSE,
        saksbehandlerIdent = null,
        saksbehandlerNavn = null,
        beslutterIdent = null,
        beslutterNavn = null,
        automatiskFattet = true,
        harArbeidsgiverOensketRefusjon = true,
    )
