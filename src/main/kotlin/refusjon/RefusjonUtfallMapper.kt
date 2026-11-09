package no.nav.helsearbeidsgiver.refusjon

import no.nav.helsearbeidsgiver.refusjonskrav.InntektsmeldingRefusjonskrav
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
    val daggrupper = utbetalingsdager.tilDaggrupper(refusjonskravPerInntektsmelding, anvendtSeksG.toBigDecimal())

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
        refusjonskrav = utbetalingsdager.tilRefusjonskrav(refusjonskravPerInntektsmelding),
        beloepUtbetaltTilArbeidsgiver = daggrupper.sumOf { it.beloepUtbetaltTilArbeidsgiver },
        arbeidstakerVedtakMelding = this,
    )
}
