package no.nav.helsearbeidsgiver.refusjon

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.refusjonskrav.InntektsmeldingRefusjonskrav
import java.time.LocalDate
import kotlin.uuid.Uuid

fun ArbeidstakerVedtakMelding.hagInntektsmeldingReferanser(): List<Uuid> =
    utbetalingsdager.mapNotNull { it.forespurtRefusjon.hagInntektsmeldingReferanse }.distinct()

fun ArbeidstakerVedtakMelding.tilRefusjonUtfall(
    refusjonUtfallId: Uuid,
    refusjonskrav: List<InntektsmeldingRefusjonskrav>,
    arbeidsgiverNavn: String,
    sykmeldtNavn: String,
): RefusjonUtfall {
    val refusjonskravPerInntektsmelding = refusjonskrav.associate { it.inntektsmeldingId to it.refusjonskrav }

    return RefusjonUtfall(
        refusjonUtfallId = refusjonUtfallId,
        vedtaksperiodeId = vedtaksperiodeId,
        fnr = foedselsnummer,
        orgnr = organisasjonsnummer,
        fom = fom,
        tom = tom,
        sykepengegrunnlag = sykepengegrunnlag,
        utfallTilArbeidsgiver = vedtaksUtfallTilArbeidsgiver.tilUtfall(),
        fattetTidspunkt = vedtakFattetTidspunkt,
        sykmeldtNavn = sykmeldtNavn,
        arbeidsgiverNavn = arbeidsgiverNavn,
        dager = utbetalingsdager.map { it.tilDag(refusjonskravPerInntektsmelding) },
        arbeidstakerVedtakMelding = this,
    )
}

private fun VedtaksUtfall.tilUtfall(): Utfall =
    when (this) {
        VedtaksUtfall.AVSLAG -> Utfall.AVSLAG
        VedtaksUtfall.DELVIS_INNVILGELSE -> Utfall.DELVIS_INNVILGELSE
        VedtaksUtfall.INNVILGELSE -> Utfall.INNVILGELSE
    }

private fun Utbetalingsdag.tilDag(refusjonskravPerInntektsmelding: Map<Uuid, Refusjon?>): Dag =
    Dag(
        dato = dato,
        dagtype = type.tilDagType(),
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
