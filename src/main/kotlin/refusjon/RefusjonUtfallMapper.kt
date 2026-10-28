package no.nav.helsearbeidsgiver.refusjon

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.refusjonskrav.InntektsmeldingRefusjonskrav
import java.time.LocalDate
import kotlin.uuid.Uuid

// TODO: Midlertidig hardkodet. Dager igjen med rett til sykepenger skal komme fra sas, og må på plass før vi sier oss ferdige.
internal const val DAGER_IGJEN_MED_RETT_TIL_SYKEPENGER = 101

fun ArbeidstakerVedtakMelding.hagInntektsmeldingReferanser(): List<Uuid> =
    utbetalingsdager.mapNotNull { it.forespurtRefusjon.hagInntektsmeldingReferanse }.distinct()

fun ArbeidstakerVedtakMelding.tilRefusjonUtfall(
    refusjonUtfallId: Uuid,
    refusjonskrav: List<InntektsmeldingRefusjonskrav>,
    arbeidsgiverNavn: String,
    sykmeldtNavn: String,
): RefusjonUtfall {
    val dager = tilDager(refusjonskrav)
    val daggrupper = dager.tilDaggrupper(anvendt6G = anvendtSeksG.toBigDecimal())

    return RefusjonUtfall(
        refusjonUtfallId = refusjonUtfallId,
        vedtaksperiodeId = vedtaksperiodeId,
        fnr = foedselsnummer,
        orgnr = organisasjonsnummer,
        fom = fom,
        tom = tom,
        sykepengegrunnlag = sykepengegrunnlag,
        utfallTilArbeidsgiver = Utfall.INNVILGELSE,
        arbeidstakervedtakFattetTidspunkt = vedtakFattetTidspunkt,
        sykmeldtNavn = sykmeldtNavn,
        arbeidsgiverNavn = arbeidsgiverNavn,
        daggrupper = daggrupper,
        beloepKrevdAvArbeidsgiver = daggrupper.sumOf { it.beloepKrevdRefusjon },
        beloepUtbetaltTilArbeidsgiver = dager.sumOf { it.beloepUtbetaltTilArbeidsgiver },
        dagerIgjenMedRettTilSykepenger = DAGER_IGJEN_MED_RETT_TIL_SYKEPENGER,
        arbeidstakerVedtakMelding = this,
    )
}

internal fun ArbeidstakerVedtakMelding.tilDager(refusjonskrav: List<InntektsmeldingRefusjonskrav>): List<Dag> {
    val refusjonskravPerInntektsmelding = refusjonskrav.associate { it.inntektsmeldingId to it.refusjonskrav }

    return utbetalingsdager.map { it.tilDag(refusjonskravPerInntektsmelding) }
}

private fun Utbetalingsdag.tilDag(refusjonskravPerInntektsmelding: Map<Uuid, Refusjon?>): Dag =
    Dag(
        dato = dato,
        dagtype = type.tilDagType(),
        sykdomsgrad = sykdomsgrad,
        beloepUtbetaltTilArbeidsgiver = beloepTilArbeidsgiver,
        beloepKrevdRefusjonPerDag = forespurtRefusjon.beloep,
        beloepKrevdRefusjonPerMaaned =
            forespurtRefusjon.hagInntektsmeldingReferanse
                ?.let { refusjonskravPerInntektsmelding[it] }
                ?.beloepPaaDato(dato),
    )

internal fun Utbetalingsdagtype.tilDagType(): DagType =
    when (this) {
        Utbetalingsdagtype.NAV_DAG,
        Utbetalingsdagtype.ARBEIDSGIVERPERIODEDAG_NAV,
        -> DagType.SYKEPENGER

        Utbetalingsdagtype.ARBEIDSGIVERPERIODE_DAG -> DagType.ARBEIDSGIVERPERIODE

        Utbetalingsdagtype.NAV_HELG_DAG -> DagType.HELG

        Utbetalingsdagtype.FRIDAG -> DagType.FRI

        Utbetalingsdagtype.FERIEDAG -> DagType.FERIE

        Utbetalingsdagtype.PERMISJONSDAG -> DagType.PERMISJON

        Utbetalingsdagtype.ARBEIDSDAG -> DagType.ARBEIDSDAG

        Utbetalingsdagtype.AVVIST_DAG,
        Utbetalingsdagtype.FORELDET_DAG,
        -> DagType.IKKE_RETT_PAA_SYKEPENGER

        Utbetalingsdagtype.VENTETIDSDAG,
        Utbetalingsdagtype.ARBEID_IKKE_GJENOPPTATT_DAG,
        Utbetalingsdagtype.ANDRE_YTELSER,
        Utbetalingsdagtype.UKJENT_DAG,
        -> DagType.UKJENT
    }

// Endringer i refusjonskravet gjelder fra og med startdato og erstatter beløpPerMåned.
internal fun Refusjon.beloepPaaDato(dato: LocalDate): Double =
    endringer
        .filter { it.startdato <= dato }
        .maxByOrNull { it.startdato }
        ?.beloep
        ?: beloepPerMaaned
