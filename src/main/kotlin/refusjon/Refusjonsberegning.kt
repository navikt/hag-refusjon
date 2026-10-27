package no.nav.helsearbeidsgiver.refusjon

import java.math.BigDecimal
import java.math.MathContext

// TODO: Midlertidig hardkodet. Anvendt 6G skal komme fra sis, og må på plass før vi sier oss ferdige.
internal val ANVENDT_6G = BigDecimal("819294")

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
        .divide(BigDecimal("26000"), MathContext.DECIMAL128) // 260 virkedager per år × 100 %
}

// Avvik på inntil 1 kr skyldes avrunding i Spleis. Høyere utbetaling enn forventet regnes ikke som redusert.
internal fun erRedusertRefusjon(
    forventetRefusjon: BigDecimal,
    faktiskRefusjon: BigDecimal,
): Boolean = forventetRefusjon - faktiskRefusjon > BigDecimal.ONE
