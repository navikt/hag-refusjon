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

// Ikke alle testorganisasjoner finnes i Brreg sitt testmiljø, så i dev brukes et fallback-navn når Brreg ikke har treff.
class VirksomhetsnavnKlientMedFallback(
    private val opprinneligKlient: VirksomhetsnavnKlient,
    private val fallback: String = UKJENT_VIRKSOMHETSNAVN,
) : VirksomhetsnavnKlient {
    override suspend fun hentVirksomhetsnavn(orgnr: Orgnr): String = opprinneligKlient.hentVirksomhetsnavn(orgnr) ?: fallback

    companion object {
        const val UKJENT_VIRKSOMHETSNAVN = "Ukjent Virksomhetsnavn"
    }
}
