package no.nav.helsearbeidsgiver.arkiv

import no.nav.helsearbeidsgiver.auth.AzureAdTokenHenter
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.Avsender
import no.nav.helsearbeidsgiver.dokarkiv.domene.Dokument
import no.nav.helsearbeidsgiver.dokarkiv.domene.DokumentVariant
import no.nav.helsearbeidsgiver.dokarkiv.domene.GjelderPerson
import no.nav.helsearbeidsgiver.dokarkiv.domene.Journalposttype
import no.nav.helsearbeidsgiver.dokarkiv.domene.Kanal
import no.nav.helsearbeidsgiver.vedtak.ArbeidstakerVedtakMelding
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

private const val TITTEL = "Refusjon av sykepenger til arbeidsgiver"
private const val BREVKODE = "REFUSJON_SYKEPENGER"

class ArkivService(
    private val dokArkivClient: DokArkivClient,
) {
    constructor(url: String, scope: String, tokenEndpoint: String) : this(
        DokArkivClient(
            url = url,
            getAccessToken = AzureAdTokenHenter(tokenEndpoint).tokenGetter(scope),
        ),
    )

    suspend fun arkiver(
        melding: ArbeidstakerVedtakMelding,
        refusjonUtfallId: UUID,
        arbeidsgiverNavn: String,
        pdf: ByteArray,
    ): String =
        dokArkivClient
            .opprettOgFerdigstillJournalpost(
                tittel = TITTEL,
                gjelderPerson = GjelderPerson(melding.foedselsnummer.verdi),
                avsender = Avsender.Organisasjon(orgnr = melding.organisasjonsnummer.verdi, navn = arbeidsgiverNavn),
                datoMottatt = LocalDate.now(),
                dokumenter =
                    listOf(
                        Dokument(
                            tittel = TITTEL,
                            brevkode = BREVKODE,
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
                    ),
                eksternReferanseId = refusjonUtfallId.toString(),
                callId = UUID.randomUUID().toString(),
                kanal = Kanal.ALTINN,
                journalposttype = Journalposttype.UTGAAENDE,
            ).journalpostId
}
