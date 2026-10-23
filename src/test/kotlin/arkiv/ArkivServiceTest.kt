package no.nav.helsearbeidsgiver.arkiv

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.Dokument
import no.nav.helsearbeidsgiver.dokarkiv.domene.DokumentVariant
import no.nav.helsearbeidsgiver.dokarkiv.domene.GjelderPerson
import no.nav.helsearbeidsgiver.dokarkiv.domene.Kanal
import no.nav.helsearbeidsgiver.dokarkiv.domene.Mottaker
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.refusjon.arbeidstakerVedtakMeldingMock
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.Base64
import java.util.UUID
import kotlin.uuid.toKotlinUuid

class ArkivServiceTest :
    FunSpec({
        test("arkiverer PDF som utgående journalpost med ARKIV-variant og returnerer journalpostId") {
            val fnr = Fnr.genererGyldig()
            val orgnr = Orgnr.genererGyldig()
            val refusjonUtfallId = UUID.randomUUID().toKotlinUuid()
            val pdf = "PDF innhold".toByteArray()

            val mottaker = slot<Mottaker>()
            val dokumenter = slot<List<Dokument>>()
            val eksternReferanseId = slot<String>()
            val kanal = slot<Kanal>()

            val dokArkivClient =
                mockk<DokArkivClient> {
                    coEvery {
                        opprettOgFerdigstillJournalpostUtgaaende(
                            tittel = "Refusjon av sykepenger til arbeidsgiver",
                            gjelderPerson = GjelderPerson(fnr.verdi),
                            mottaker = capture(mottaker),
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
                    melding = arbeidstakerVedtakMeldingMock(fnr, orgnr),
                    refusjonUtfallId = refusjonUtfallId,
                    arbeidsgiverNavn = "Billys Bollefabrikk AS",
                    pdf = pdf,
                )

            journalpostId shouldBe "123456789"
            mottaker.captured shouldBe Mottaker.Organisasjon(orgnr = orgnr.verdi, navn = "Billys Bollefabrikk AS")
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
                                    filnavn = "refusjon-$refusjonUtfallId.pdf",
                                ),
                            ),
                    ),
                )
        }
    })
