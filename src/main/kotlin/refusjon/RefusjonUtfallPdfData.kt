@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.refusjon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
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
    val dager: List<Dag>,
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
        dager = dager,
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
