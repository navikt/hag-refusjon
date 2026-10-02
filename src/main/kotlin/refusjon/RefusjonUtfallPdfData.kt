@file:UseSerializers(UuidSerializer::class, LocalDateSerializer::class, LocalDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.vedtak

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// Datagrunnlag for refusjon-malen i helsearbeidsgiver-pdfgen. Feltnavn og @SerialName må synkroniseres med malen.
@Serializable
data class RefusjonUtfallPdfData(
    @SerialName("fødselsnummer") val foedselsnummer: Fnr,
    val yrkesaktivitetstype: Yrkesaktivitetstype,
    val organisasjonsnummer: Orgnr,
    val arbeidsgiverNavn: String,
    val vedtaksperiodeId: UUID,
    val fom: LocalDate,
    val tom: LocalDate,
    @SerialName("skjæringstidspunkt") val skjaeringstidspunkt: LocalDate,
    val sykepengegrunnlag: Double,
    val utbetalingsdager: List<Utbetalingsdag>,
    val vedtakFattetTidspunkt: LocalDateTime,
    val vedtaksUtfallTilArbeidsgiver: VedtaksUtfall,
    val saksbehandlerIdent: String?,
    val saksbehandlerNavn: String?,
    val beslutterIdent: String?,
    val beslutterNavn: String?,
    val automatiskFattet: Boolean,
    @SerialName("harArbeidsgiverØnsketRefusjon") val harArbeidsgiverOensketRefusjon: Boolean,
)

fun ArbeidstakerVedtakMelding.tilRefusjonUtfallPdfData(arbeidsgiverNavn: String): RefusjonUtfallPdfData =
    RefusjonUtfallPdfData(
        foedselsnummer = foedselsnummer,
        yrkesaktivitetstype = yrkesaktivitetstype,
        organisasjonsnummer = organisasjonsnummer,
        arbeidsgiverNavn = arbeidsgiverNavn,
        vedtaksperiodeId = vedtaksperiodeId,
        fom = fom,
        tom = tom,
        skjaeringstidspunkt = skjaeringstidspunkt,
        sykepengegrunnlag = sykepengegrunnlag,
        utbetalingsdager = utbetalingsdager,
        vedtakFattetTidspunkt = vedtakFattetTidspunkt,
        vedtaksUtfallTilArbeidsgiver = vedtaksUtfallTilArbeidsgiver,
        saksbehandlerIdent = saksbehandlerIdent,
        saksbehandlerNavn = saksbehandlerNavn,
        beslutterIdent = beslutterIdent,
        beslutterNavn = beslutterNavn,
        automatiskFattet = automatiskFattet,
        harArbeidsgiverOensketRefusjon = harArbeidsgiverOensketRefusjon,
    )
