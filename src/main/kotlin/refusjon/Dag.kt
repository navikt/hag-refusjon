package no.nav.helsearbeidsgiver.refusjon

import java.time.LocalDate

// Internt mellomsteg mellom utbetalingsdagene fra sis og daggruppene i RefusjonUtfall.
data class Dag(
    val dato: LocalDate,
    val dagtype: Dagtype,
    val sykdomsgrad: Int,
    val beloepUtbetaltTilArbeidsgiver: Int,
    val beloepKrevdRefusjonPerDag: Int,
    val beloepKrevdRefusjonPerMaaned: Double?,
)

enum class Dagtype {
    NAV_DAG,
    ARBEIDSGIVERPERIODE,
    HELG,
    FRI,
    FERIE,
    PERMISJON,
    ARBEIDSDAG,
    IKKE_RETT_PAA_SYKEPENGER,
    UKJENT,
}
