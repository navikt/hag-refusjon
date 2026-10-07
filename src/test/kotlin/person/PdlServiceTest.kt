package no.nav.helsearbeidsgiver.person

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.pdl.domene.PersonNavn
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr

private val FNR = Fnr.genererGyldig()

class PdlServiceTest :
    FunSpec({
        context("PdlService") {
            test("setter sammen fornavn, mellomnavn og etternavn") {
                val pdlClient =
                    mockk<PdlClient> {
                        coEvery { personNavn(FNR.verdi) } returns PersonNavn("Ola", "Mellom", "Nordmann")
                    }

                PdlService(pdlClient).hentSykmeldtnavn(FNR) shouldBe "Ola Mellom Nordmann"
            }

            test("utelater mellomnavn når det mangler") {
                val pdlClient =
                    mockk<PdlClient> {
                        coEvery { personNavn(FNR.verdi) } returns PersonNavn("Ola", null, "Nordmann")
                    }

                PdlService(pdlClient).hentSykmeldtnavn(FNR) shouldBe "Ola Nordmann"
            }

            test("gir navn fra PDL stor forbokstav, også med bindestrek") {
                val pdlClient =
                    mockk<PdlClient> {
                        coEvery { personNavn(FNR.verdi) } returns PersonNavn("OLA-KARI", "MELLOM", "NORDMANN")
                    }

                PdlService(pdlClient).hentSykmeldtnavn(FNR) shouldBe "Ola-Kari Mellom Nordmann"
            }

            test("gir null når PDL ikke har person") {
                val pdlClient =
                    mockk<PdlClient> {
                        coEvery { personNavn(FNR.verdi) } returns null
                    }

                PdlService(pdlClient).hentSykmeldtnavn(FNR).shouldBeNull()
            }

            test("kaster videre når PDL feiler") {
                val pdlClient =
                    mockk<PdlClient> {
                        coEvery { personNavn(FNR.verdi) } throws RuntimeException("pdl nede")
                    }

                shouldThrow<RuntimeException> { PdlService(pdlClient).hentSykmeldtnavn(FNR) }
            }
        }
    })
