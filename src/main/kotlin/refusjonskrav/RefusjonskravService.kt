package no.nav.helsearbeidsgiver.refusjonskrav

import no.nav.helsearbeidsgiver.auth.AzureAdTokenHenter
import java.util.UUID

class RefusjonskravService(
    private val refusjonskravKlient: RefusjonskravKlient,
) {
    constructor(url: String, scope: String, tokenEndpoint: String) : this(
        refusjonskravKlient =
            SpinosaurusRefusjonskravKlient(
                url,
                getAccessToken = AzureAdTokenHenter(tokenEndpoint).tokenGetter(scope),
            ),
    )

    suspend fun hentRefusjonskrav(inntektsmeldingIder: List<UUID>): List<InntektsmeldingRefusjonskrav> =
        refusjonskravKlient.hentRefusjonskrav(inntektsmeldingIder)
}
