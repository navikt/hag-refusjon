package no.nav.helsearbeidsgiver.refusjon

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.refusjonskrav.InntektsmeldingRefusjonskrav
import java.time.LocalDate
import kotlin.uuid.Uuid

fun ArbeidstakerVedtakMelding.tilDager(refusjonskrav: List<InntektsmeldingRefusjonskrav>): List<Dag> {
    val refusjonskravPerInntektsmelding = refusjonskrav.associate { it.inntektsmeldingId to it.refusjonskrav }

    return utbetalingsdager.map { it.tilDag(refusjonskravPerInntektsmelding) }
}

private fun Utbetalingsdag.tilDag(refusjonskravPerInntektsmelding: Map<Uuid, Refusjon?>): Dag =
    Dag(
        dato = dato,
        dagtype = tilDagType(),
        sykdomsgrad = sykdomsgrad,
        beloepUtbetaltTilArbeidsgiver = beloepTilArbeidsgiver,
        beloepKrevdRefusjonPerDag = forespurtRefusjon.beloep,
        beloepKrevdRefusjonPerMaaned =
            forespurtRefusjon.hagInntektsmeldingReferanse
                ?.let { refusjonskravPerInntektsmelding[it] }
                ?.beloepPaaDato(dato),
    )

fun Utbetalingsdag.tilDagType(): Dagtype =
    when (type) {
        Utbetalingsdagtype.NAV_DAG -> Dagtype.NAV_DAG

        // Spleis slår sammen de to arbeidsgiverperiodetypene, så beloepTilArbeidsgiver avgjør om Nav dekker dagen.
        Utbetalingsdagtype.ARBEIDSGIVERPERIODE_DAG,
        Utbetalingsdagtype.ARBEIDSGIVERPERIODEDAG_NAV,
        -> if (beloepTilArbeidsgiver > 0) Dagtype.NAV_DAG else Dagtype.ARBEIDSGIVERPERIODE

        Utbetalingsdagtype.NAV_HELG_DAG -> Dagtype.HELG

        Utbetalingsdagtype.FRIDAG -> Dagtype.FRI

        Utbetalingsdagtype.FERIEDAG -> Dagtype.FERIE

        Utbetalingsdagtype.PERMISJONSDAG -> Dagtype.PERMISJON

        Utbetalingsdagtype.ARBEIDSDAG -> Dagtype.ARBEIDSDAG

        Utbetalingsdagtype.AVVIST_DAG,
        Utbetalingsdagtype.FORELDET_DAG,
        -> Dagtype.IKKE_RETT_PAA_SYKEPENGER

        Utbetalingsdagtype.VENTETIDSDAG,
        Utbetalingsdagtype.ARBEID_IKKE_GJENOPPTATT_DAG,
        Utbetalingsdagtype.ANDRE_YTELSER,
        Utbetalingsdagtype.UKJENT_DAG,
        -> Dagtype.UKJENT
    }

// Endringer i refusjonskravet gjelder fra og med startdato og erstatter beløpPerMåned.
fun Refusjon.beloepPaaDato(dato: LocalDate): Double =
    endringer
        .filter { it.startdato <= dato }
        .maxByOrNull { it.startdato }
        ?.beloep
        ?: beloepPerMaaned
