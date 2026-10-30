package no.nav.helsearbeidsgiver.bakgrunnsjobb

import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakMelding
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

const val JOB_TYPE = "ARBEIDSTAKERVEDTAK"

class ArbeidstakerVedtakJobb : BakgrunnsjobbProsesserer {
    override fun prosesser(jobb: Bakgrunnsjobb) {
        val skjema = jobb.dataJson?.fromJson(ArbeidstakerVedtakMelding.serializer())
        if (skjema != null) {
            sikkerLogger().debug("Bakgrunnsjobb: Behandler melding: {}", skjema)
            // vedtakService.behandle(skjema)
        }
    }

    override val type: String
        get() = JOB_TYPE
}
