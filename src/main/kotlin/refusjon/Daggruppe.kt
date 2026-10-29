package no.nav.helsearbeidsgiver.refusjon

import kotlinx.serialization.Serializable

@Serializable
data class Daggruppe(
    val antallDager: Int,
    val sykepengeStatus: SykepengeStatus,
    val sykdomsgrad: Int,
    // Forventet refusjon per dag, summert for gruppen. Bruker refusjonskravet per måned fra inntektsmeldingen × 12,
    // ellers refusjonskravet per dag fra sis × 260. Begrenset til 6G, justert for sykdomsgrad og rundet til hele kroner per dag.
    val beloepForventetRefusjon: Int,
    // Refusjonskrav per dag fra sis (forespurtRefusjon), uendret. Ikke begrenset til 6G eller justert for sykdomsgrad.
    val beloepKrevdRefusjonPerDag: Int,
    // Refusjonskrav per måned fra inntektsmeldingen som gjelder på datoen, uendret. Null hvis vedtaket ikke peker på en inntektsmelding.
    val beloepKrevdRefusjonPerMaaned: Double?,
    // Summen av beløp til arbeidsgiver fra sis for dagene i gruppen. Beregnet av Spleis, som begrenser til 6G og justerer for sykdomsgrad.
    val beloepUtbetaltTilArbeidsgiver: Int,
    val begrensetTilSeksG: Boolean,
)

@Serializable
enum class SykepengeStatus {
    RETT_TIL_SYKEPENGER,

    // Rett til sykepenger, men beløpet utbetalt til arbeidsgiver avviker med mer enn 1 kr fra forventet refusjon.
    // Forventet refusjon er refusjonskravet begrenset til 6G og justert for sykdomsgrad.
    RETT_TIL_SYKEPENGER_DIFFERANSE,

    IKKE_RETT_TIL_SYKEPENGER,
}
