@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.refusjon

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.uuid.Uuid

// Datagrunnlag for refusjon-malen i helsearbeidsgiver-pdfgen. Feltnavnene må synkroniseres med malen.
@Serializable
data class RefusjonUtfallPdfData(
    val refusjonUtfallId: Uuid,
    val fnr: Fnr,
    val sykmeldtNavn: String,
    val orgnr: Orgnr,
    val arbeidsgiverNavn: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val daggrupper: List<Daggruppe>,
    val beloepForventetRefusjon: Int,
    val beloepUtbetaltTilArbeidsgiver: Int,
    val dagerIgjenMedRettTilSykepenger: Int,
    val arbeidstakervedtakFattetTidspunkt: LocalDateTime,
)

fun RefusjonUtfall.tilRefusjonUtfallPdfData(): RefusjonUtfallPdfData =
    RefusjonUtfallPdfData(
        refusjonUtfallId = refusjonUtfallId,
        fnr = fnr,
        sykmeldtNavn = sykmeldtNavn,
        orgnr = orgnr,
        arbeidsgiverNavn = arbeidsgiverNavn,
        fom = fom,
        tom = tom,
        daggrupper = daggrupper,
        beloepForventetRefusjon = beloepForventetRefusjon,
        beloepUtbetaltTilArbeidsgiver = beloepUtbetaltTilArbeidsgiver,
        dagerIgjenMedRettTilSykepenger = dagerIgjenMedRettTilSykepenger,
        arbeidstakervedtakFattetTidspunkt = arbeidstakervedtakFattetTidspunkt,
    )
