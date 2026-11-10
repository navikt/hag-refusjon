package no.nav.helsearbeidsgiver.refusjon

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import java.math.BigDecimal
import kotlin.uuid.Uuid

// Påfølgende dager med lik status, sykdomsgrad og beløp utbetalt per dag slås sammen til én daggruppe.
// Dager som er filtrert bort (f.eks. helg) bryter ikke en daggruppe.
fun List<Utbetalingsdag>.tilDaggrupper(
    refusjonskravPerInntektsmelding: Map<Uuid, Refusjon?>,
    anvendtSeksG: BigDecimal,
): List<Daggruppe> =
    sortedBy { it.dato }
        .mapNotNull { it.tilDaggruppe(refusjonskravPerInntektsmelding, anvendtSeksG) }
        .fold(emptyList()) { daggrupper, neste ->
            val forrige = daggrupper.lastOrNull()
            if (forrige != null && forrige.kanSlaasSammenMed(neste)) {
                daggrupper.dropLast(1) + forrige.slaaSammenMed(neste)
            } else {
                daggrupper + neste
            }
        }

private fun Utbetalingsdag.tilDaggruppe(
    refusjonskravPerInntektsmelding: Map<Uuid, Refusjon?>,
    anvendtSeksG: BigDecimal,
): Daggruppe? {
    val sykepengeStatus = tilSykepengeStatus(refusjonskravPerMaaned(refusjonskravPerInntektsmelding), anvendtSeksG)

    // Dager utenfor daggruppene skal ikke ha utbetaling til arbeidsgiver
    if (sykepengeStatus == null) {
        check(beloepTilArbeidsgiver == 0) {
            "Fant utbetaling til arbeidsgiver på $dato med dagtype $type. Dette er ikke forventet og vi feiler derfor refusjonsberegningen."
        }
        return null
    }

    return Daggruppe(
        antallDager = 1,
        sykepengeStatus = sykepengeStatus,
        sykdomsgrad = sykdomsgrad,
        beloepUtbetaltTilArbeidsgiverPerDag = beloepTilArbeidsgiver,
        beloepUtbetaltTilArbeidsgiver = beloepTilArbeidsgiver,
    )
}

private fun Utbetalingsdag.tilSykepengeStatus(
    refusjonskravPerMaaned: Double?,
    anvendtSeksG: BigDecimal,
): SykepengeStatus? =
    when (type) {
        Utbetalingsdagtype.NAV_DAG -> {
            tilStatusMedRettTilSykepenger(refusjonskravPerMaaned, anvendtSeksG)
        }

        // Spleis slår sammen de to arbeidsgiverperiodetypene, så beloepTilArbeidsgiver avgjør om Nav dekker dagen.
        Utbetalingsdagtype.ARBEIDSGIVERPERIODE_DAG,
        Utbetalingsdagtype.ARBEIDSGIVERPERIODEDAG_NAV,
        -> {
            if (beloepTilArbeidsgiver > 0) tilStatusMedRettTilSykepenger(refusjonskravPerMaaned, anvendtSeksG) else null
        }

        Utbetalingsdagtype.AVVIST_DAG,
        Utbetalingsdagtype.FORELDET_DAG,
        -> {
            SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER
        }

        Utbetalingsdagtype.NAV_HELG_DAG,
        Utbetalingsdagtype.FRIDAG,
        Utbetalingsdagtype.FERIEDAG,
        Utbetalingsdagtype.PERMISJONSDAG,
        Utbetalingsdagtype.ARBEIDSDAG,
        Utbetalingsdagtype.VENTETIDSDAG,
        Utbetalingsdagtype.ARBEID_IKKE_GJENOPPTATT_DAG,
        Utbetalingsdagtype.ANDRE_YTELSER,
        Utbetalingsdagtype.UKJENT_DAG,
        -> {
            null
        }
    }

// Uten refusjonskrav fra inntektsmelding har vi ikke noe forventet beløp å sammenligne med.
private fun Utbetalingsdag.tilStatusMedRettTilSykepenger(
    refusjonskravPerMaaned: Double?,
    anvendtSeksG: BigDecimal,
): SykepengeStatus {
    if (refusjonskravPerMaaned == null) return SykepengeStatus.RETT_TIL_SYKEPENGER

    val forventetRefusjon = forventetRefusjonPerDag(refusjonskravPerAar(refusjonskravPerMaaned), anvendtSeksG, sykdomsgrad)

    return if (erUtbetaltLavereEnnForventet(forventetRefusjon, beloepTilArbeidsgiver.toBigDecimal())) {
        SykepengeStatus.RETT_TIL_SYKEPENGER_DIFFERANSE
    } else {
        SykepengeStatus.RETT_TIL_SYKEPENGER
    }
}

private fun Daggruppe.kanSlaasSammenMed(neste: Daggruppe): Boolean =
    sykepengeStatus == neste.sykepengeStatus &&
        sykdomsgrad == neste.sykdomsgrad &&
        beloepUtbetaltTilArbeidsgiverPerDag == neste.beloepUtbetaltTilArbeidsgiverPerDag

private fun Daggruppe.slaaSammenMed(neste: Daggruppe): Daggruppe =
    copy(
        antallDager = antallDager + neste.antallDager,
        beloepUtbetaltTilArbeidsgiver = beloepUtbetaltTilArbeidsgiver + neste.beloepUtbetaltTilArbeidsgiver,
    )
