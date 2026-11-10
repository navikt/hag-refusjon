package no.nav.helsearbeidsgiver.refusjon

import kotlinx.serialization.Serializable

@Serializable
data class Daggruppe(
    val antallDager: Int,
    val sykepengeStatus: SykepengeStatus,
    val sykdomsgrad: Int,
    // Beløp til arbeidsgiver fra sis per dag. Likt for alle dagene i gruppen.
    val beloepUtbetaltTilArbeidsgiverPerDag: Int,
    // Summen av beløp til arbeidsgiver fra sis for dagene i gruppen. Beregnet av Spleis, som begrenser til 6G og justerer for sykdomsgrad.
    val beloepUtbetaltTilArbeidsgiver: Int,
)

@Serializable
enum class SykepengeStatus {
    RETT_TIL_SYKEPENGER,

    // Rett til sykepenger, men beløpet utbetalt til arbeidsgiver er mer enn 1 kr lavere enn forventet refusjon.
    // Forventet refusjon er refusjonskravet fra inntektsmeldingen begrenset til 6G og justert for sykdomsgrad.
    // Dager uten refusjonskrav fra inntektsmelding får aldri denne statusen.
    RETT_TIL_SYKEPENGER_DIFFERANSE,

    IKKE_RETT_TIL_SYKEPENGER,
}
