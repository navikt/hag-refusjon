package no.nav.helsearbeidsgiver.refusjonskrav

import no.nav.helsearbeidsgiver.auth.AzureAdTokenHenter
import java.util.UUID

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

    suspend fun hentRefusjonskrav(inntektsmeldingIder: List<UUID>): List<InntektsmeldingRefusjonskrav> {
        val refusjonskrav = hentFraDatabase(inntektsmeldingIder)
        val manglende = inntektsmeldingIder.distinct() - refusjonskrav.map { it.inntektsmeldingId }.toSet()
        if (manglende.isEmpty()) return refusjonskrav

        return refusjonskrav + hentFraKlientOgLagre(manglende)
    }

    private fun hentFraDatabase(inntektsmeldingIder: List<UUID>): List<InntektsmeldingRefusjonskrav> = repository.hent(inntektsmeldingIder)

    private suspend fun hentFraKlientOgLagre(inntektsmeldingIder: List<UUID>): List<InntektsmeldingRefusjonskrav> =
        refusjonskravKlient.hentRefusjonskrav(inntektsmeldingIder).also { repository.lagre(it) }
}
