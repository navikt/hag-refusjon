package no.nav.helsearbeidsgiver.refusjon

import no.nav.helsearbeidsgiver.refusjonskrav.InntektsmeldingRefusjonskrav
import kotlin.uuid.Uuid

// TODO: Midlertidig hardkodet. Dager igjen med rett til sykepenger skal komme fra sas, og må på plass før vi sier oss ferdige.
const val DAGER_IGJEN_MED_RETT_TIL_SYKEPENGER = 101

fun ArbeidstakerVedtakMelding.hagInntektsmeldingReferanser(): List<Uuid> =
    utbetalingsdager.mapNotNull { it.forespurtRefusjon.hagInntektsmeldingReferanse }.distinct()

fun ArbeidstakerVedtakMelding.tilRefusjonUtfall(
    refusjonUtfallId: Uuid,
    refusjonskrav: List<InntektsmeldingRefusjonskrav>,
    arbeidsgiverNavn: String,
    sykmeldtNavn: String,
): RefusjonUtfall {
    val daggrupper = tilDager(refusjonskrav).tilDaggrupper(anvendt6G = anvendtSeksG.toBigDecimal())

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
        beloepForventetRefusjon = daggrupper.sumOf { it.beloepForventetRefusjon },
        beloepUtbetaltTilArbeidsgiver = daggrupper.sumOf { it.beloepUtbetaltTilArbeidsgiver },
        dagerIgjenMedRettTilSykepenger = DAGER_IGJEN_MED_RETT_TIL_SYKEPENGER,
        arbeidstakerVedtakMelding = this,
    )
}
