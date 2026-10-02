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

// Brreg har ikke testmiljø, så i dev hardkodes virksomhetsnavnet i stedet for å hentes fra Brreg.
class DevVirksomhetsnavnKlient : VirksomhetsnavnKlient {
    override suspend fun hentVirksomhetsnavn(orgnr: Orgnr): String = DEV_VIRKSOMHETSNAVN

    companion object {
        const val DEV_VIRKSOMHETSNAVN = "Sindig Alvevennlig Radagast AS"
    }
}
