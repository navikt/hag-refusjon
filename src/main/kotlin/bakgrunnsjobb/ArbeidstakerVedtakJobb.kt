package no.nav.helsearbeidsgiver.bakgrunnsjobb

import kotlinx.coroutines.runBlocking
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.hag.utils.bakgrunnsjobb.UtsettJobbException
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakMelding
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakRepository
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakStatus
import no.nav.helsearbeidsgiver.refusjon.VedtakService
import no.nav.helsearbeidsgiver.utils.json.fromJson
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

const val JOB_TYPE = "ARBEIDSTAKERVEDTAK"

class ArbeidstakerVedtakJobb(
    private val vedtakService: VedtakService,
    private val arbeidstakerVedtakRepository: ArbeidstakerVedtakRepository,
    private val utsettelse: Duration = 5.minutes,
) : BakgrunnsjobbProsesserer {
    override fun prosesser(jobb: Bakgrunnsjobb) {
        val melding = jobb.tilMelding()

        // Vedtak på samme vedtaksperiode skal behandles i den rekkefølgen de ble mottatt.
        val eldreUbehandlede = arbeidstakerVedtakRepository.hentStatusForEldreUbehandlede(melding.behandlingId)
        val ider = "behandlingId ${melding.behandlingId} og vedtaksperiodeId ${melding.vedtaksperiodeId}"
        if (ArbeidstakerVedtakStatus.STOPPET in eldreUbehandlede) {
            throw IllegalStateException(
                "Et eldre arbeidstakervedtak på samme vedtaksperiode er stoppet. Behandler ikke vedtaket med $ider før det er håndtert.",
            )
        }
        if (eldreUbehandlede.isNotEmpty()) {
            throw UtsettJobbException(
                utsettelse,
                "Venter på ${eldreUbehandlede.size} eldre arbeidstakervedtak før vedtaket med $ider behandles.",
            )
        }

        runBlocking { vedtakService.behandle(melding) }
        arbeidstakerVedtakRepository.oppdaterStatus(melding.behandlingId, ArbeidstakerVedtakStatus.BEHANDLET)
    }

    override fun stoppet(jobb: Bakgrunnsjobb) {
        arbeidstakerVedtakRepository.oppdaterStatus(jobb.tilMelding().behandlingId, ArbeidstakerVedtakStatus.STOPPET)
    }

    override val type: String
        get() = JOB_TYPE
}

private fun Bakgrunnsjobb.tilMelding(): ArbeidstakerVedtakMelding =
    checkNotNull(dataJson) { "Bakgrunnsjobb $uuid mangler data." }
        .fromJson(ArbeidstakerVedtakMelding.serializer())
