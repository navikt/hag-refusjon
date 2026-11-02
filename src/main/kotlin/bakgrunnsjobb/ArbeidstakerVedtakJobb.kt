package no.nav.helsearbeidsgiver.bakgrunnsjobb

import kotlinx.coroutines.runBlocking
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakMelding
import no.nav.helsearbeidsgiver.refusjon.VedtakService
import no.nav.helsearbeidsgiver.utils.json.fromJson

const val JOB_TYPE = "ARBEIDSTAKERVEDTAK"

class ArbeidstakerVedtakJobb(
    private val vedtakService: VedtakService,
) : BakgrunnsjobbProsesserer {
    override fun prosesser(jobb: Bakgrunnsjobb) {
        val melding =
            checkNotNull(jobb.dataJson) { "Bakgrunnsjobb ${jobb.uuid} mangler data." }
                .fromJson(ArbeidstakerVedtakMelding.serializer())
        runBlocking { vedtakService.behandle(melding) }
    }

    override val type: String
        get() = JOB_TYPE
}
