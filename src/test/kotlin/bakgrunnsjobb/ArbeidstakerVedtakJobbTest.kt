package no.nav.helsearbeidsgiver.bakgrunnsjobb

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.helsearbeidsgiver.refusjon.FNR
import no.nav.helsearbeidsgiver.refusjon.ORGNR
import no.nav.helsearbeidsgiver.refusjon.VedtakService
import no.nav.helsearbeidsgiver.refusjon.gyldigMelding
import no.nav.helsearbeidsgiver.utils.json.parseJson
import kotlin.uuid.Uuid

class ArbeidstakerVedtakJobbTest :
    FunSpec({
        test("prosesser behandler meldingen i jobben med VedtakService") {
            val vedtakService = mockk<VedtakService> { coEvery { behandle(any()) } returns Uuid.random() }
            val jobb = Bakgrunnsjobb(type = JOB_TYPE, dataJson = gyldigMelding(FNR, ORGNR).parseJson())

            ArbeidstakerVedtakJobb(vedtakService).prosesser(jobb)

            coVerify(exactly = 1) {
                vedtakService.behandle(match { it.foedselsnummer == FNR && it.organisasjonsnummer == ORGNR })
            }
        }

        test("prosesser feiler når VedtakService feiler, slik at jobben forsøkes på nytt") {
            val vedtakService = mockk<VedtakService> { coEvery { behandle(any()) } throws IllegalStateException("feil") }
            val jobb = Bakgrunnsjobb(type = JOB_TYPE, dataJson = gyldigMelding(FNR, ORGNR).parseJson())

            shouldThrow<IllegalStateException> { ArbeidstakerVedtakJobb(vedtakService).prosesser(jobb) }
        }

        test("prosesser feiler når jobben mangler data") {
            val vedtakService = mockk<VedtakService>()

            shouldThrow<IllegalStateException> {
                ArbeidstakerVedtakJobb(vedtakService).prosesser(Bakgrunnsjobb(type = JOB_TYPE, dataJson = null))
            }

            coVerify(exactly = 0) { vedtakService.behandle(any()) }
        }
    })
