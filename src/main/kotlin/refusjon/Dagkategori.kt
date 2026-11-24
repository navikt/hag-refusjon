package no.nav.helsearbeidsgiver.refusjon

enum class Dagkategori {
    MED_RETT_TIL_SYKEPENGER,
    ARBEIDSGIVERPERIODE,
    UTEN_RETT_TIL_SYKEPENGER,
    UTENFOR_DAGGRUPPER,
}

fun Utbetalingsdagtype.tilDagkategori(): Dagkategori =
    when (this) {
        Utbetalingsdagtype.NAV_DAG -> Dagkategori.MED_RETT_TIL_SYKEPENGER

        Utbetalingsdagtype.ARBEIDSGIVERPERIODE_DAG,
        Utbetalingsdagtype.ARBEIDSGIVERPERIODEDAG_NAV,
        -> Dagkategori.ARBEIDSGIVERPERIODE

        Utbetalingsdagtype.AVVIST_DAG,
        Utbetalingsdagtype.FORELDET_DAG,
        -> Dagkategori.UTEN_RETT_TIL_SYKEPENGER

        Utbetalingsdagtype.NAV_HELG_DAG,
        Utbetalingsdagtype.FRIDAG,
        Utbetalingsdagtype.FERIEDAG,
        Utbetalingsdagtype.PERMISJONSDAG,
        Utbetalingsdagtype.ARBEIDSDAG,
        Utbetalingsdagtype.VENTETIDSDAG,
        Utbetalingsdagtype.ARBEID_IKKE_GJENOPPTATT_DAG,
        Utbetalingsdagtype.ANDRE_YTELSER,
        Utbetalingsdagtype.UKJENT_DAG,
        -> Dagkategori.UTENFOR_DAGGRUPPER
    }

val Utbetalingsdagtype.kanHaUtbetalingTilArbeidsgiver: Boolean
    get() = tilDagkategori() in setOf(Dagkategori.MED_RETT_TIL_SYKEPENGER, Dagkategori.ARBEIDSGIVERPERIODE)

val Utbetalingsdagtype.kanInngaaIDaggruppe: Boolean
    get() = tilDagkategori() != Dagkategori.UTENFOR_DAGGRUPPER
