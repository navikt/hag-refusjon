package no.nav.helsearbeidsgiver.refusjon

import kotlin.uuid.Uuid

private val dagtyperMedUtbetalingTilArbeidsgiver =
    setOf(
        Utbetalingsdagtype.NAV_DAG,
        Utbetalingsdagtype.ARBEIDSGIVERPERIODE_DAG,
        Utbetalingsdagtype.ARBEIDSGIVERPERIODEDAG_NAV,
    )

object ArbeidstakerVedtakValidator {
    fun valider(melding: ArbeidstakerVedtakMelding): List<ArbeidstakerVedtakValideringsfeil> =
        ArbeidstakerVedtakValideringsregel.entries
            .filterNot { regel -> melding.oppfyller(regel) }
            .map { it.feil }
}

private fun ArbeidstakerVedtakMelding.oppfyller(regel: ArbeidstakerVedtakValideringsregel): Boolean = regel.krav(this)

enum class ArbeidstakerVedtakValideringsregel(
    val feil: ArbeidstakerVedtakValideringsfeil,
    val krav: ArbeidstakerVedtakMelding.() -> Boolean,
) {
    HAR_SYKMELDING(
        ArbeidstakerVedtakValideringsfeil.MANGLER_SYKMELDING,
        { dokumenter.any { it.type == Dokument.Type.Sykmelding } },
    ),
    FOM_ER_FOER_ELLER_SAMME_DAG_SOM_TOM(
        ArbeidstakerVedtakValideringsfeil.UGYLDIG_PERIODE,
        { fom <= tom },
    ),
    ANVENDT_SEKS_G_ER_POSITIV(
        ArbeidstakerVedtakValideringsfeil.UGYLDIG_ANVENDT_SEKS_G,
        { anvendtSeksG > 0 },
    ),
    SYKEPENGEGRUNNLAG_ER_IKKE_NEGATIVT(
        ArbeidstakerVedtakValideringsfeil.NEGATIVT_SYKEPENGEGRUNNLAG,
        { sykepengegrunnlag >= 0 },
    ),
    HAR_UTBETALINGSDAGER(
        ArbeidstakerVedtakValideringsfeil.MANGLER_UTBETALINGSDAGER,
        { utbetalingsdager.isNotEmpty() },
    ),
    BELOEP_TIL_ARBEIDSGIVER_ER_IKKE_NEGATIVT(
        ArbeidstakerVedtakValideringsfeil.NEGATIVT_BELOEP_TIL_ARBEIDSGIVER,
        { utbetalingsdager.all { it.beloepTilArbeidsgiver >= 0 } },
    ),
    UTBETALING_TIL_ARBEIDSGIVER_KUN_PAA_FORVENTEDE_DAGTYPER(
        ArbeidstakerVedtakValideringsfeil.UVENTET_UTBETALING_TIL_ARBEIDSGIVER_PAA_DAGTYPE,
        { utbetalingsdager.none { it.beloepTilArbeidsgiver > 0 && it.type !in dagtyperMedUtbetalingTilArbeidsgiver } },
    ),
    UTBETALINGSDAGER_ER_INNENFOR_VEDTAKSPERIODEN(
        ArbeidstakerVedtakValideringsfeil.UTBETALINGSDAG_UTENFOR_VEDTAKSPERIODE,
        { utbetalingsdager.all { it.dato in fom..tom } },
    ),
    UTBETALINGSDATOER_ER_UNIKE(
        ArbeidstakerVedtakValideringsfeil.DUPLIKAT_UTBETALINGSDATO,
        { utbetalingsdager.map { it.dato }.let { it.size == it.toSet().size } },
    ),
    SYKDOMSGRAD_ER_MELLOM_0_OG_100(
        ArbeidstakerVedtakValideringsfeil.UGYLDIG_SYKDOMSGRAD,
        { utbetalingsdager.all { it.sykdomsgrad in 0..100 } },
    ),
}

enum class ArbeidstakerVedtakValideringsfeil(
    val beskrivelse: String,
) {
    MANGLER_SYKMELDING("Vedtaket mangler referanse til sykmelding"),
    UGYLDIG_PERIODE("Fra-og-med-dato er etter til-og-med-dato"),
    UGYLDIG_ANVENDT_SEKS_G("Anvendt 6G er null eller lavere"),
    NEGATIVT_SYKEPENGEGRUNNLAG("Sykepengegrunnlaget er negativt"),
    MANGLER_UTBETALINGSDAGER("Vedtaket mangler utbetalingsdager"),
    NEGATIVT_BELOEP_TIL_ARBEIDSGIVER("Beløp til arbeidsgiver er negativt på minst én utbetalingsdag"),
    UVENTET_UTBETALING_TIL_ARBEIDSGIVER_PAA_DAGTYPE("Utbetaling til arbeidsgiver på minst én dagtype der det ikke er forventet"),
    UTBETALINGSDAG_UTENFOR_VEDTAKSPERIODE("Minst én utbetalingsdag ligger utenfor vedtaksperioden"),
    DUPLIKAT_UTBETALINGSDATO("Samme utbetalingsdato forekommer flere ganger"),
    UGYLDIG_SYKDOMSGRAD("Sykdomsgrad er under 0 eller over 100 på minst én utbetalingsdag"),
}

class UgyldigArbeidstakerVedtakException(
    behandlingId: Uuid,
    vedtaksperiodeId: Uuid,
    val feil: List<ArbeidstakerVedtakValideringsfeil>,
) : IllegalStateException() {
    override val message: String =
        "Ugyldig arbeidstakervedtak med behandlingId $behandlingId og vedtaksperiodeId $vedtaksperiodeId: " +
            feil.joinToString { "${it.name} (${it.beskrivelse})" }
}
