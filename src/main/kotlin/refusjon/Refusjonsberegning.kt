package no.nav.helsearbeidsgiver.refusjon

import java.math.BigDecimal
import java.math.MathContext

private val MAANEDER_PER_AAR = BigDecimal("12")
private val VIRKEDAGER_PER_AAR = BigDecimal("260")
private val HUNDRE_PROSENT = BigDecimal("100")
private val AVRUNDINGSTOLERANSE = BigDecimal.ONE

// Refusjonskravet fra inntektsmeldingen er førstevalg. Mangler det, brukes kravet per dag fra sis.
internal fun refusjonskravPerAar(
    refusjonskravPerMaaned: Double?,
    refusjonskravPerDag: Int,
): BigDecimal =
    refusjonskravPerMaaned?.toBigDecimal()?.multiply(MAANEDER_PER_AAR)
        ?: refusjonskravPerDag.toBigDecimal().multiply(VIRKEDAGER_PER_AAR)

internal fun erBegrensetTil6G(
    refusjonskravPerAar: BigDecimal,
    anvendt6G: BigDecimal,
): Boolean = refusjonskravPerAar > anvendt6G

internal fun forventetRefusjonPerDag(
    refusjonskravPerAar: BigDecimal,
    anvendt6G: BigDecimal,
    sykdomsgrad: Int,
): BigDecimal {
    require(refusjonskravPerAar.signum() >= 0)
    require(anvendt6G.signum() > 0)
    require(sykdomsgrad in 0..100)

    return refusjonskravPerAar
        .min(anvendt6G)
        .multiply(sykdomsgrad.toBigDecimal())
        .divide(VIRKEDAGER_PER_AAR * HUNDRE_PROSENT, MathContext.DECIMAL128)
}

// Avvik på inntil 1 kr i begge retninger skyldes avrunding i Spleis.
internal fun erDifferanseIRefusjon(
    forventetRefusjon: BigDecimal,
    faktiskRefusjon: BigDecimal,
): Boolean = (faktiskRefusjon - forventetRefusjon).abs() > AVRUNDINGSTOLERANSE
