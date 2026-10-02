package no.nav.helsearbeidsgiver.vedtak

import kotlinx.serialization.Serializable

// Datagrunnlag for refusjon-malen i helsearbeidsgiver-pdfgen. Feltnavn må synkroniseres med malen.
@Serializable
data class RefusjonUtfallPdfData(
    val arbeidstakerVedtak: ArbeidstakerVedtakMelding,
    val arbeidsgiverNavn: String,
)
