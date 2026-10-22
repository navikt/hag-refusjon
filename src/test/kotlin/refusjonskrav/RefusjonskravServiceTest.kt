package no.nav.helsearbeidsgiver.refusjonskrav

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class RefusjonskravServiceTest :
    FunSpec({
        val klient = mockk<RefusjonskravKlient>()
        val repository = mockk<RefusjonskravRepository>()
        val service = RefusjonskravService(klient, repository)

        beforeTest { clearAllMocks() }

        test("henter kun manglende fra klient og lagrer dem") {
            val lagret = InntektsmeldingRefusjonskrav(UUID.randomUUID(), null)
            val nytt = InntektsmeldingRefusjonskrav(UUID.randomUUID(), null)
            every { repository.hent(any()) } returns listOf(lagret)
            coEvery { klient.hentRefusjonskrav(listOf(nytt.inntektsmeldingId)) } returns listOf(nytt)
            every { repository.lagre(listOf(nytt)) } returns listOf(nytt.inntektsmeldingId)

            service.hentRefusjonskrav(listOf(lagret.inntektsmeldingId, nytt.inntektsmeldingId)) shouldContainExactlyInAnyOrder
                listOf(lagret, nytt)

            verify { repository.lagre(listOf(nytt)) }
        }

        test("kaller ikke klient når alt allerede er lagret") {
            val lagret = InntektsmeldingRefusjonskrav(UUID.randomUUID(), null)
            every { repository.hent(any()) } returns listOf(lagret)

            service.hentRefusjonskrav(listOf(lagret.inntektsmeldingId)) shouldContainExactlyInAnyOrder listOf(lagret)

            coVerify(exactly = 0) { klient.hentRefusjonskrav(any()) }
        }
    })
