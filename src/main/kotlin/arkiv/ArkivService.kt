package no.nav.helsearbeidsgiver.arkiv

import no.nav.helsearbeidsgiver.auth.AzureAdTokenHenter
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.Dokument
import no.nav.helsearbeidsgiver.dokarkiv.domene.DokumentVariant
import no.nav.helsearbeidsgiver.dokarkiv.domene.GjelderPerson
import no.nav.helsearbeidsgiver.dokarkiv.domene.Kanal
import no.nav.helsearbeidsgiver.dokarkiv.domene.Mottaker
import no.nav.helsearbeidsgiver.refusjon.RefusjonUtfall
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
        refusjonUtfall: RefusjonUtfall,
        pdf: ByteArray,
    ): String =
        dokArkivClient
            .opprettOgFerdigstillJournalpostUtgaaende(
                tittel = TITTEL,
                gjelderPerson = GjelderPerson(refusjonUtfall.fnr.verdi),
                mottaker = Mottaker.Organisasjon(orgnr = refusjonUtfall.orgnr.verdi, navn = refusjonUtfall.arbeidsgiverNavn),
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
                                        filnavn = "refusjon-${refusjonUtfall.refusjonUtfallId}.pdf",
                                    ),
                                ),
                        ),
                    ),
                eksternReferanseId = refusjonUtfall.refusjonUtfallId.toString(),
                callId = UUID.randomUUID().toString(),
                kanal = Kanal.ALTINN,
            ).journalpostId
}
