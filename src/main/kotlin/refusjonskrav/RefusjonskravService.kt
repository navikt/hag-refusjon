package no.nav.helsearbeidsgiver.refusjonskrav

import no.nav.helsearbeidsgiver.auth.AzureAdTokenHenter
import kotlin.uuid.Uuid

class RefusjonskravService(
    private val refusjonskravKlient: RefusjonskravKlient,
    private val repository: RefusjonskravRepository,
) {
    constructor(url: String, scope: String, tokenEndpoint: String, repository: RefusjonskravRepository) : this(
        refusjonskravKlient =
            RefusjonskravKlient(
                url,
                getAccessToken = AzureAdTokenHenter(tokenEndpoint).tokenGetter(scope),
            ),
        repository = repository,
    )

    suspend fun hentRefusjonskrav(inntektsmeldingIder: List<Uuid>): List<InntektsmeldingRefusjonskrav> {
        val refusjonskrav = hentFraDatabase(inntektsmeldingIder)
        val manglende = inntektsmeldingIder.distinct() - refusjonskrav.map { it.inntektsmeldingId }.toSet()
        if (manglende.isEmpty()) return refusjonskrav

        return refusjonskrav + hentFraKlientOgLagre(manglende)
    }

    private fun hentFraDatabase(inntektsmeldingIder: List<Uuid>): List<InntektsmeldingRefusjonskrav> = repository.hent(inntektsmeldingIder)

    private suspend fun hentFraKlientOgLagre(inntektsmeldingIder: List<Uuid>): List<InntektsmeldingRefusjonskrav> =
        refusjonskravKlient.hentRefusjonskrav(inntektsmeldingIder).also { repository.lagre(it) }
}
