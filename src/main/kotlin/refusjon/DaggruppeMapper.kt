package no.nav.helsearbeidsgiver.refusjon

import java.math.BigDecimal
import java.time.LocalDate

// Internt mellomsteg mellom utbetalingsdagene fra sis og daggruppene i RefusjonUtfall.
internal data class Dag(
    val dato: LocalDate,
    val dagtype: Dagtype,
    val sykdomsgrad: Int,
    val beloepUtbetaltTilArbeidsgiver: Int,
    val beloepKrevdRefusjonPerDag: Int,
    val beloepKrevdRefusjonPerMaaned: Double?,
)

internal enum class Dagtype {
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

// Påfølgende dager med lik status, sykdomsgrad, refusjonskrav og 6G-begrensning slås sammen til én daggruppe.
// Dager som er filtrert bort (f.eks. helg) bryter ikke en daggruppe.
internal fun List<Dag>.tilDaggrupper(anvendt6G: BigDecimal): List<Daggruppe> =
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
    val refusjonskravPerAar = refusjonskravPerAar(beloepKrevdRefusjonPerMaaned, beloepKrevdRefusjonPerDag)

    return tilSykepengeStatus(refusjonskravPerAar, anvendt6G)?.let { sykepengeStatus ->
        Daggruppe(
            antallDager = 1,
            sykepengeStatus = sykepengeStatus,
            sykdomsgrad = sykdomsgrad,
            beloepKrevdRefusjonPerDag = beloepKrevdRefusjonPerDag,
            beloepKrevdRefusjonPerMaaned = beloepKrevdRefusjonPerMaaned,
            beloepKrevdRefusjon = beloepKrevdRefusjonPerDag,
            beloepUtbetaltTilArbeidsgiver = beloepUtbetaltTilArbeidsgiver,
            begrensetTil6G =
                sykepengeStatus != SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER && erBegrensetTil6G(refusjonskravPerAar, anvendt6G),
        )
    }
}

private fun Dag.tilSykepengeStatus(
    refusjonskravPerAar: BigDecimal,
    anvendt6G: BigDecimal,
): SykepengeStatus? =
    when (dagtype) {
        Dagtype.NAV_DAG -> {
            if (harDifferanseIRefusjon(refusjonskravPerAar, anvendt6G)) {
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

private fun Dag.harDifferanseIRefusjon(
    refusjonskravPerAar: BigDecimal,
    anvendt6G: BigDecimal,
): Boolean =
    erDifferanseIRefusjon(
        forventetRefusjon = forventetRefusjonPerDag(refusjonskravPerAar, anvendt6G, sykdomsgrad),
        faktiskRefusjon = beloepUtbetaltTilArbeidsgiver.toBigDecimal(),
    )

private fun Daggruppe.kanSlaasSammenMed(neste: Daggruppe): Boolean =
    sykepengeStatus == neste.sykepengeStatus &&
        sykdomsgrad == neste.sykdomsgrad &&
        beloepKrevdRefusjonPerDag == neste.beloepKrevdRefusjonPerDag &&
        beloepKrevdRefusjonPerMaaned == neste.beloepKrevdRefusjonPerMaaned &&
        begrensetTil6G == neste.begrensetTil6G

private fun Daggruppe.slaaSammenMed(neste: Daggruppe): Daggruppe =
    copy(
        antallDager = antallDager + neste.antallDager,
        beloepKrevdRefusjon = beloepKrevdRefusjon + neste.beloepKrevdRefusjon,
        beloepUtbetaltTilArbeidsgiver = beloepUtbetaltTilArbeidsgiver + neste.beloepUtbetaltTilArbeidsgiver,
    )
