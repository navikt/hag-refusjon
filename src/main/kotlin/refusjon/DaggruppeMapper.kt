package no.nav.helsearbeidsgiver.refusjon

import java.math.BigDecimal

// Påfølgende dager med lik status, sykdomsgrad, refusjonskrav og 6G-begrensning slås sammen til én daggruppe.
// Dager som er filtrert bort (f.eks. helg) bryter ikke en daggruppe.
fun List<Dag>.tilDaggrupper(anvendt6G: BigDecimal): List<Daggruppe> =
    sortedBy { it.dato }
        .mapNotNull { it.tilDaggruppe(anvendt6G) }
        .fold(emptyList()) { daggrupper, neste ->
            val forrige = daggrupper.lastOrNull()
            if (forrige != null && forrige.kanSlaasSammenMed(neste)) {
                daggrupper.dropLast(1) + forrige.slaaSammenMed(neste)
            } else {
                daggrupper + neste
            }
        }

private fun Dag.tilDaggruppe(anvendt6G: BigDecimal): Daggruppe? {
    val refusjonskravPerAar =
        refusjonskravPerAar(beloepKrevdRefusjonPerMaaned, beloepKrevdRefusjonPerDag)
    val forventetRefusjon = forventetRefusjonPerDag(refusjonskravPerAar, anvendt6G, sykdomsgrad)
    val sykepengeStatus = tilSykepengeStatus(forventetRefusjon)

    // Dager utenfor daggruppene skal ikke ha utbetaling til arbeidsgiver
    if (sykepengeStatus == null) {
        check(beloepUtbetaltTilArbeidsgiver == 0) {
            "Fant utbetaling til arbeidsgiver på $dato med dagtype $dagtype. Dette er ikke forventet og vi feiler derfor refusjonsberegningen."
        }
        return null
    }

    return Daggruppe(
        antallDager = 1,
        sykepengeStatus = sykepengeStatus,
        sykdomsgrad = sykdomsgrad,
        beloepKrevdRefusjonPerDag = beloepKrevdRefusjonPerDag,
        beloepKrevdRefusjonPerMaaned = beloepKrevdRefusjonPerMaaned,
        beloepForventetRefusjon = forventetRefusjon.tilHeleKroner(),
        beloepUtbetaltTilArbeidsgiver = beloepUtbetaltTilArbeidsgiver,
        begrensetTil6G =
            sykepengeStatus != SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER && erBegrensetTil6G(refusjonskravPerAar, anvendt6G),
    )
}

private fun Dag.tilSykepengeStatus(forventetRefusjon: BigDecimal): SykepengeStatus? =
    when (dagtype) {
        Dagtype.NAV_DAG -> {
            if (erDifferanseIRefusjon(forventetRefusjon, beloepUtbetaltTilArbeidsgiver.toBigDecimal())) {
                SykepengeStatus.RETT_TIL_SYKEPENGER_DIFFERANSE
            } else {
                SykepengeStatus.RETT_TIL_SYKEPENGER
            }
        }

        Dagtype.IKKE_RETT_PAA_SYKEPENGER -> {
            SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER
        }

        Dagtype.ARBEIDSGIVERPERIODE,
        Dagtype.HELG,
        Dagtype.FRI,
        Dagtype.FERIE,
        Dagtype.PERMISJON,
        Dagtype.ARBEIDSDAG,
        Dagtype.UKJENT,
        -> {
            null
        }
    }

private fun Daggruppe.kanSlaasSammenMed(neste: Daggruppe): Boolean =
    sykepengeStatus == neste.sykepengeStatus &&
        sykdomsgrad == neste.sykdomsgrad &&
        beloepKrevdRefusjonPerDag == neste.beloepKrevdRefusjonPerDag &&
        beloepKrevdRefusjonPerMaaned == neste.beloepKrevdRefusjonPerMaaned &&
        begrensetTil6G == neste.begrensetTil6G

private fun Daggruppe.slaaSammenMed(neste: Daggruppe): Daggruppe =
    copy(
        antallDager = antallDager + neste.antallDager,
        beloepForventetRefusjon = beloepForventetRefusjon + neste.beloepForventetRefusjon,
        beloepUtbetaltTilArbeidsgiver = beloepUtbetaltTilArbeidsgiver + neste.beloepUtbetaltTilArbeidsgiver,
    )
