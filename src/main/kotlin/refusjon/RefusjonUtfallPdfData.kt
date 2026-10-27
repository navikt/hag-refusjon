@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.refusjon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.uuid.Uuid

// Datagrunnlag for refusjon-malen i helsearbeidsgiver-pdfgen. Feltnavn og @SerialName må synkroniseres med malen.
@Serializable
data class RefusjonUtfallPdfData(
    @SerialName("fødselsnummer") val foedselsnummer: Fnr,
    val sykmeldtNavn: String,
    val yrkesaktivitetstype: Yrkesaktivitetstype,
    val organisasjonsnummer: Orgnr,
    val arbeidsgiverNavn: String,
    val vedtaksperiodeId: Uuid,
    val fom: LocalDate,
    val tom: LocalDate,
    @SerialName("skjæringstidspunkt") val skjaeringstidspunkt: LocalDate,
    val sykepengegrunnlag: Double,
    val daggrupper: List<Daggruppe>,
    val utbetaltTilArbeidsgiver: Int,
    val vedtakFattetTidspunkt: LocalDateTime,
    val vedtaksUtfallTilArbeidsgiver: VedtaksUtfall,
    val saksbehandlerIdent: String?,
    val saksbehandlerNavn: String?,
    val beslutterIdent: String?,
    val beslutterNavn: String?,
    val automatiskFattet: Boolean,
    @SerialName("harArbeidsgiverØnsketRefusjon") val harArbeidsgiverOensketRefusjon: Boolean,
)

@Serializable
data class Daggruppe(
    val antallDager: Int,
    val sykepengeStatus: SykepengeStatus,
    val sykdomsgrad: Int,
    val beloepKrevdRefusjonPerDag: Int,
    val beloepKrevdRefusjonPerMaaned: Double?,
    val beloepUtbetaltTilArbeidsgiver: Int,
    val begrensetTil6G: Boolean,
)

@Serializable
enum class SykepengeStatus {
    RETT_TIL_SYKEPENGER,
    RETT_TIL_SYKEPENGER_REDUSERT,
    IKKE_RETT_TIL_SYKEPENGER,
}

fun RefusjonUtfall.tilRefusjonUtfallPdfData(): RefusjonUtfallPdfData =
    RefusjonUtfallPdfData(
        foedselsnummer = fnr,
        sykmeldtNavn = sykmeldtNavn,
        yrkesaktivitetstype = arbeidstakerVedtakMelding.yrkesaktivitetstype,
        organisasjonsnummer = orgnr,
        arbeidsgiverNavn = arbeidsgiverNavn,
        vedtaksperiodeId = vedtaksperiodeId,
        fom = fom,
        tom = tom,
        skjaeringstidspunkt = arbeidstakerVedtakMelding.skjaeringstidspunkt,
        sykepengegrunnlag = sykepengegrunnlag,
        daggrupper = tilDaggrupper(),
        utbetaltTilArbeidsgiver = dager.sumOf { it.beloepUtbetaltTilArbeidsgiver },
        vedtakFattetTidspunkt = fattetTidspunkt,
        vedtaksUtfallTilArbeidsgiver = arbeidstakerVedtakMelding.vedtaksUtfallTilArbeidsgiver,
        saksbehandlerIdent = arbeidstakerVedtakMelding.saksbehandlerIdent,
        saksbehandlerNavn = arbeidstakerVedtakMelding.saksbehandlerNavn,
        beslutterIdent = arbeidstakerVedtakMelding.beslutterIdent,
        beslutterNavn = arbeidstakerVedtakMelding.beslutterNavn,
        automatiskFattet = arbeidstakerVedtakMelding.automatiskFattet,
        harArbeidsgiverOensketRefusjon = arbeidstakerVedtakMelding.harArbeidsgiverOensketRefusjon,
    )

// Påfølgende dager med lik status, sykdomsgrad, refusjonskrav og 6G-begrensning slås sammen til én daggruppe.
// Dager som er filtrert bort (f.eks. helg) bryter ikke en daggruppe.
private fun RefusjonUtfall.tilDaggrupper(): List<Daggruppe> {
    val beloepKrevdRefusjonPerMaanedPerDato = dager.associate { it.dato to it.beloepKrevdRefusjonPerMaaned }

    return arbeidstakerVedtakMelding.utbetalingsdager
        .sortedBy { it.dato }
        .mapNotNull { it.tilDaggruppe(beloepKrevdRefusjonPerMaanedPerDato[it.dato]) }
        .fold(emptyList()) { daggrupper, neste ->
            val forrige = daggrupper.lastOrNull()
            if (forrige != null && forrige.kanSlaasSammenMed(neste)) {
                daggrupper.dropLast(1) + forrige.slaaSammenMed(neste)
            } else {
                daggrupper + neste
            }
        }
}

private fun Utbetalingsdag.tilDaggruppe(beloepKrevdRefusjonPerMaaned: Double?): Daggruppe? {
    val refusjonskravPerAar = refusjonskravPerAar(beloepKrevdRefusjonPerMaaned)

    return tilSykepengeStatus(refusjonskravPerAar)?.let { sykepengeStatus ->
        Daggruppe(
            antallDager = 1,
            sykepengeStatus = sykepengeStatus,
            sykdomsgrad = sykdomsgrad,
            beloepKrevdRefusjonPerDag = forespurtRefusjon.beloep,
            beloepKrevdRefusjonPerMaaned = beloepKrevdRefusjonPerMaaned,
            beloepUtbetaltTilArbeidsgiver = beloepTilArbeidsgiver,
            begrensetTil6G = sykepengeStatus != SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER && refusjonskravPerAar > ANVENDT_6G,
        )
    }
}

// Refusjonskravet fra inntektsmeldingen er førstevalg. Mangler det, brukes kravet per dag fra sis.
private fun Utbetalingsdag.refusjonskravPerAar(beloepKrevdRefusjonPerMaaned: Double?): BigDecimal =
    beloepKrevdRefusjonPerMaaned?.toBigDecimal()?.multiply(BigDecimal("12"))
        ?: forespurtRefusjon.beloep.toBigDecimal().multiply(BigDecimal("260"))

private fun Utbetalingsdag.tilSykepengeStatus(refusjonskravPerAar: BigDecimal): SykepengeStatus? =
    when (type) {
        Utbetalingsdagtype.NAV_DAG,
        Utbetalingsdagtype.ARBEIDSGIVERPERIODEDAG_NAV,
        -> {
            if (harRedusertRefusjon(refusjonskravPerAar)) {
                SykepengeStatus.RETT_TIL_SYKEPENGER_REDUSERT
            } else {
                SykepengeStatus.RETT_TIL_SYKEPENGER
            }
        }

        Utbetalingsdagtype.AVVIST_DAG,
        Utbetalingsdagtype.FORELDET_DAG,
        -> {
            SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER
        }

        Utbetalingsdagtype.ARBEIDSGIVERPERIODE_DAG,
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

private fun Utbetalingsdag.harRedusertRefusjon(refusjonskravPerAar: BigDecimal): Boolean =
    erRedusertRefusjon(
        forventetRefusjon = forventetRefusjonPerDag(refusjonskravPerAar, ANVENDT_6G, sykdomsgrad),
        faktiskRefusjon = beloepTilArbeidsgiver.toBigDecimal(),
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
        beloepUtbetaltTilArbeidsgiver = beloepUtbetaltTilArbeidsgiver + neste.beloepUtbetaltTilArbeidsgiver,
    )
