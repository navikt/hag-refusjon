package no.nav.helsearbeidsgiver.refusjon

import java.math.BigDecimal
import java.math.MathContext

private val MAANEDER_PER_AAR = BigDecimal("12")
private val VIRKEDAGER_PER_AAR = BigDecimal("260")
private val HUNDRE_PROSENT = BigDecimal("100")
private val AVRUNDINGSTOLERANSE = BigDecimal.ONE

fun refusjonskravPerAar(refusjonskravPerMaaned: Double): BigDecimal = refusjonskravPerMaaned.toBigDecimal().multiply(MAANEDER_PER_AAR)

fun forventetRefusjonPerDag(
    refusjonskravPerAar: BigDecimal,
    anvendtSeksG: BigDecimal,
    sykdomsgrad: Int,
): BigDecimal {
    require(refusjonskravPerAar.signum() >= 0)
    require(anvendtSeksG.signum() > 0)
    require(sykdomsgrad in 0..100)

    return refusjonskravPerAar
        .min(anvendtSeksG)
        .multiply(sykdomsgrad.toBigDecimal())
        .divide(VIRKEDAGER_PER_AAR * HUNDRE_PROSENT, MathContext.DECIMAL128)
}

fun erUtbetaltLavereEnnForventet(
    forventetRefusjon: BigDecimal,
    faktiskRefusjon: BigDecimal,
): Boolean = forventetRefusjon - faktiskRefusjon > AVRUNDINGSTOLERANSE
