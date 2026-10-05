package no.nav.helsearbeidsgiver.person

import no.nav.helsearbeidsgiver.auth.AzureAdTokenHenter
import no.nav.helsearbeidsgiver.pdl.Behandlingsgrunnlag
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.giNavnStorForbokstav
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import kotlin.time.Duration.Companion.minutes

class PdlService(
    private val pdlClient: PdlClient,
) {
    constructor(url: String, scope: String, tokenEndpoint: String) : this(
        PdlClient(
            url = url,
            behandlingsgrunnlag = Behandlingsgrunnlag.SYKEPENGER,
            cacheConfig = LocalCache.Config(entryDuration = 1.minutes, maxEntries = 1_000),
            getAccessToken = AzureAdTokenHenter(tokenEndpoint).tokenGetter(scope),
        ),
    )

    suspend fun hentSykmeldtnavn(fnr: Fnr): String? =
        pdlClient
            .personNavn(fnr.verdi)
            ?.fulltNavn()
            ?.giNavnStorForbokstav()
}
