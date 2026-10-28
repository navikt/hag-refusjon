package no.nav.helsearbeidsgiver.refusjon

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.RoundingMode

class RefusjonsberegningTest :
    FunSpec({
        context("refusjonskravPerAar") {
            test("ganger refusjonskrav per måned med 12") {
                refusjonskravPerAar(refusjonskravPerMaaned = 26000.5, refusjonskravPerDag = 1000) shouldBeEqualComparingTo
                    BigDecimal("312006")
            }

            test("bruker refusjonskrav per dag ganger 260 når refusjonskrav per måned mangler") {
                refusjonskravPerAar(refusjonskravPerMaaned = null, refusjonskravPerDag = 1200) shouldBeEqualComparingTo
                    BigDecimal("312000")
            }
        }

        context("erBegrensetTil6G") {
            withData(
                nameFn = { (krav, forventet) -> "refusjonskrav $krav gir $forventet" },
                "819295" to true,
                "819294" to false,
                "819293.99" to false,
            ) { (krav, forventet) ->
                erBegrensetTil6G(BigDecimal(krav), ANVENDT_6G) shouldBe forventet
            }
        }

        context("forventetRefusjonPerDag") {
            test("deler årlig refusjonskrav på 260 virkedager") {
                forventetRefusjonPerDag(BigDecimal("312000"), ANVENDT_6G, sykdomsgrad = 100) shouldBeEqualComparingTo BigDecimal("1200")
            }

            test("tar høyde for sykdomsgrad") {
                forventetRefusjonPerDag(BigDecimal("312000"), ANVENDT_6G, sykdomsgrad = 50) shouldBeEqualComparingTo BigDecimal("600")
            }

            test("begrenser refusjonskravet til 6G") {
                forventetRefusjonPerDag(BigDecimal("1000000"), BigDecimal("780000"), sykdomsgrad = 100) shouldBeEqualComparingTo
                    BigDecimal("3000")
            }

            test("runder ikke av underveis") {
                forventetRefusjonPerDag(ANVENDT_6G, ANVENDT_6G, sykdomsgrad = 100)
                    .setScale(4, RoundingMode.HALF_UP) shouldBeEqualComparingTo BigDecimal("3151.1308")
            }

            context("kaster feil ved ugyldig input") {
                withData(
                    nameFn = { it.first },
                    "negativt refusjonskrav" to { forventetRefusjonPerDag(BigDecimal("-1"), ANVENDT_6G, 100) },
                    "6G er null" to { forventetRefusjonPerDag(BigDecimal("312000"), BigDecimal.ZERO, 100) },
                    "sykdomsgrad over 100" to { forventetRefusjonPerDag(BigDecimal("312000"), ANVENDT_6G, 101) },
                    "negativ sykdomsgrad" to { forventetRefusjonPerDag(BigDecimal("312000"), ANVENDT_6G, -1) },
                ) { (_, beregning) ->
                    shouldThrow<IllegalArgumentException> { beregning() }
                }
            }
        }

        context("erDifferanseIRefusjon") {
            withData(
                nameFn = { (faktisk, forventetResultat) -> "faktisk $faktisk mot forventet 1200 gir $forventetResultat" },
                "1200" to false,
                "1199" to false,
                "1201" to false,
                "1198.99" to true,
                "1201.01" to true,
            ) { (faktisk, forventetResultat) ->
                erDifferanseIRefusjon(forventetRefusjon = BigDecimal("1200"), faktiskRefusjon = BigDecimal(faktisk)) shouldBe
                    forventetResultat
            }
        }
    })
