package no.nav.helsearbeidsgiver.refusjon

import java.math.BigDecimal
import java.time.LocalDate

// Internt mellomsteg mellom utbetalingsdagene fra sis og daggruppene i RefusjonUtfall.
internal data class Dag(
    val dato: LocalDate,
    val dagtype: DagType,
    val sykdomsgrad: Int,
    val beloepUtbetaltTilArbeidsgiver: Int,
    val beloepKrevdRefusjonPerDag: Int,
    val beloepKrevdRefusjonPerMaaned: Double?,
)

internal enum class DagType {
    SYKEPENGER,
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
internal fun List<Dag>.tilDaggrupper(): List<Daggruppe> =
    sortedBy { it.dato }
        .mapNotNull { it.tilDaggruppe() }
        .fold(emptyList()) { daggrupper, neste ->
            val forrige = daggrupper.lastOrNull()
            if (forrige != null && forrige.kanSlaasSammenMed(neste)) {
                daggrupper.dropLast(1) + forrige.slaaSammenMed(neste)
            } else {
                daggrupper + neste
            }
        }

private fun Dag.tilDaggruppe(): Daggruppe? {
    val refusjonskravPerAar = refusjonskravPerAar(beloepKrevdRefusjonPerMaaned, beloepKrevdRefusjonPerDag)

    return tilSykepengeStatus(refusjonskravPerAar)?.let { sykepengeStatus ->
        Daggruppe(
            antallDager = 1,
            sykepengeStatus = sykepengeStatus,
            sykdomsgrad = sykdomsgrad,
            beloepKrevdRefusjonPerDag = beloepKrevdRefusjonPerDag,
            beloepKrevdRefusjonPerMaaned = beloepKrevdRefusjonPerMaaned,
            beloepKrevdRefusjon = beloepKrevdRefusjonPerDag,
            beloepUtbetaltTilArbeidsgiver = beloepUtbetaltTilArbeidsgiver,
            begrensetTil6G =
                sykepengeStatus != SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER && erBegrensetTil6G(refusjonskravPerAar, ANVENDT_6G),
        )
    }
}

private fun Dag.tilSykepengeStatus(refusjonskravPerAar: BigDecimal): SykepengeStatus? =
    when (dagtype) {
        DagType.SYKEPENGER -> {
            if (harDifferanseIRefusjon(refusjonskravPerAar)) {
                SykepengeStatus.RETT_TIL_SYKEPENGER_DIFFERANSE
            } else {
                SykepengeStatus.RETT_TIL_SYKEPENGER
            }
        }

        DagType.IKKE_RETT_PAA_SYKEPENGER -> {
            SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER
        }

        DagType.ARBEIDSGIVERPERIODE,
        DagType.HELG,
        DagType.FRI,
        DagType.FERIE,
        DagType.PERMISJON,
        DagType.ARBEIDSDAG,
        DagType.UKJENT,
        -> {
            null
        }
    }

private fun Dag.harDifferanseIRefusjon(refusjonskravPerAar: BigDecimal): Boolean =
    erDifferanseIRefusjon(
        forventetRefusjon = forventetRefusjonPerDag(refusjonskravPerAar, ANVENDT_6G, sykdomsgrad),
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
