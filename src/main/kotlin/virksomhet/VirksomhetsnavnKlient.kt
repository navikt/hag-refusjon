package no.nav.helsearbeidsgiver.virksomhet

import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

interface VirksomhetsnavnKlient {
    suspend fun hentVirksomhetsnavn(orgnr: Orgnr): String?
}

class BrregVirksomhetsnavnKlient(
    private val brregClient: BrregClient,
) : VirksomhetsnavnKlient {
    override suspend fun hentVirksomhetsnavn(orgnr: Orgnr): String? = brregClient.hentOrganisasjonNavn(setOf(orgnr.verdi))[orgnr]
}
