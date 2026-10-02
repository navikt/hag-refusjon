package no.nav.helsearbeidsgiver.virksomhet

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

class VirksomhetsnavnKlientTest :
    FunSpec({
        val orgnr = Orgnr("896929119")
        val virksomhetsnavn = "Sprellende Sei Fiskeri AS"

        context("BrregVirksomhetsnavnKlient") {
            test("henter virksomhetsnavn for orgnr fra Brreg") {
                val brregClient =
                    mockk<BrregClient> {
                        coEvery { hentOrganisasjonNavn(setOf(orgnr.verdi)) } returns mapOf(orgnr to virksomhetsnavn)
                    }

                BrregVirksomhetsnavnKlient(brregClient).hentVirksomhetsnavn(orgnr) shouldBe virksomhetsnavn

                coVerify(exactly = 1) { brregClient.hentOrganisasjonNavn(setOf(orgnr.verdi)) }
            }

            test("gir null når Brreg ikke har navn for orgnr") {
                val brregClient =
                    mockk<BrregClient> {
                        coEvery { hentOrganisasjonNavn(any()) } returns emptyMap()
                    }

                BrregVirksomhetsnavnKlient(brregClient).hentVirksomhetsnavn(orgnr).shouldBeNull()
            }
        }

        context("DevVirksomhetsnavnKlient") {
            test("gir hardkodet virksomhetsnavn") {
                DevVirksomhetsnavnKlient().hentVirksomhetsnavn(orgnr) shouldBe DevVirksomhetsnavnKlient.DEV_VIRKSOMHETSNAVN
            }
        }
    })
