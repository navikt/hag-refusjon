package no.nav.helsearbeidsgiver.virksomhet

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

private const val VIRKSOMHETSNAVN = "Sprellende Sei Fiskeri AS"
private val ORGNR = Orgnr("896929119")

class VirksomhetsnavnKlientTest :
    FunSpec({
        context("BrregVirksomhetsnavnKlient") {
            test("henter virksomhetsnavn for orgnr fra Brreg") {
                val brregClient =
                    mockk<BrregClient> {
                        coEvery { hentOrganisasjonNavn(setOf(ORGNR.verdi)) } returns mapOf(ORGNR to VIRKSOMHETSNAVN)
                    }

                BrregVirksomhetsnavnKlient(brregClient).hentVirksomhetsnavn(ORGNR) shouldBe VIRKSOMHETSNAVN

                coVerify(exactly = 1) { brregClient.hentOrganisasjonNavn(setOf(ORGNR.verdi)) }
            }

            test("gir null når Brreg ikke har navn for orgnr") {
                val brregClient =
                    mockk<BrregClient> {
                        coEvery { hentOrganisasjonNavn(any()) } returns emptyMap()
                    }

                BrregVirksomhetsnavnKlient(brregClient).hentVirksomhetsnavn(ORGNR).shouldBeNull()
            }
        }
    })
