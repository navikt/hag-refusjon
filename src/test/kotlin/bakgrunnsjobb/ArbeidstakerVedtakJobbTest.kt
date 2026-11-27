package no.nav.helsearbeidsgiver.bakgrunnsjobb

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.UtsettJobbException
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakMelding
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakRepository
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakStatus
import no.nav.helsearbeidsgiver.refusjon.FNR
import no.nav.helsearbeidsgiver.refusjon.ORGNR
import no.nav.helsearbeidsgiver.refusjon.VedtakService
import no.nav.helsearbeidsgiver.refusjon.gyldigMelding
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import kotlin.time.Duration.Companion.minutes

class ArbeidstakerVedtakJobbTest :
    FunSpec({
        val vedtakService = mockk<VedtakService>()
        val repository = mockk<ArbeidstakerVedtakRepository>(relaxUnitFun = true)
        val jobb = Bakgrunnsjobb(type = JOB_TYPE, dataJson = gyldigMelding(FNR, ORGNR).parseJson())
        val behandlingId = gyldigMelding(FNR, ORGNR).fromJson(ArbeidstakerVedtakMelding.serializer()).behandlingId

        fun arbeidstakerVedtakJobb() = ArbeidstakerVedtakJobb(vedtakService, repository, utsettelse = 5.minutes)

        beforeTest {
            clearAllMocks()
            coEvery { vedtakService.behandle(any()) } returns Unit
            every { repository.hentStatusForEldreUbehandlede(any()) } returns emptyList()
        }

        test("behandler meldingen med VedtakService og markerer vedtaket som behandlet") {
            arbeidstakerVedtakJobb().prosesser(jobb)

            coVerify(exactly = 1) {
                vedtakService.behandle(match { it.foedselsnummer == FNR && it.organisasjonsnummer == ORGNR })
            }
            verify(exactly = 1) { repository.oppdaterStatus(behandlingId, ArbeidstakerVedtakStatus.BEHANDLET) }
        }

        test("utsetter jobben når et eldre vedtak på samme vedtaksperiode ikke er behandlet") {
            every { repository.hentStatusForEldreUbehandlede(behandlingId) } returns listOf(ArbeidstakerVedtakStatus.MOTTATT)

            val feil = shouldThrow<UtsettJobbException> { arbeidstakerVedtakJobb().prosesser(jobb) }

            feil.utsettelse shouldBe 5.minutes
            coVerify(exactly = 0) { vedtakService.behandle(any()) }
            verify(exactly = 0) { repository.oppdaterStatus(any(), any()) }
        }

        test("feiler når et eldre vedtak på samme vedtaksperiode er stoppet") {
            every { repository.hentStatusForEldreUbehandlede(behandlingId) } returns
                listOf(ArbeidstakerVedtakStatus.MOTTATT, ArbeidstakerVedtakStatus.STOPPET)

            shouldThrow<IllegalStateException> { arbeidstakerVedtakJobb().prosesser(jobb) }

            coVerify(exactly = 0) { vedtakService.behandle(any()) }
            verify(exactly = 0) { repository.oppdaterStatus(any(), any()) }
        }

        test("feiler og markerer ikke vedtaket som behandlet når VedtakService feiler") {
            coEvery { vedtakService.behandle(any()) } throws IllegalStateException("feil")

            shouldThrow<IllegalStateException> { arbeidstakerVedtakJobb().prosesser(jobb) }

            verify(exactly = 0) { repository.oppdaterStatus(any(), any()) }
        }

        test("feiler når jobben mangler data") {
            shouldThrow<IllegalStateException> {
                arbeidstakerVedtakJobb().prosesser(Bakgrunnsjobb(type = JOB_TYPE, dataJson = null))
            }

            coVerify(exactly = 0) { vedtakService.behandle(any()) }
        }

        test("markerer vedtaket som stoppet") {
            arbeidstakerVedtakJobb().stoppet(jobb)

            verify(exactly = 1) { repository.oppdaterStatus(behandlingId, ArbeidstakerVedtakStatus.STOPPET) }
        }
    })
