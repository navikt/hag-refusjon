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

@Serializable
data class RefusjonUtfall(
    val refusjonUtfallId: Uuid,
    val vedtaksperiodeId: Uuid,
    val fnr: Fnr,
    val orgnr: Orgnr,
    val fom: LocalDate,
    val tom: LocalDate,
    // Fra vedtaket i sis, uendret.
    val sykepengegrunnlag: Double,
    @Deprecated("Er alltid INNVILGELSE og skal fjernes. Beholdes fordi lps-api krever feltet.")
    val utfallTilArbeidsgiver: Utfall,
    val arbeidstakervedtakFattetTidspunkt: LocalDateTime,
    val sykmeldtNavn: String,
    val arbeidsgiverNavn: String,
    val daggrupper: List<Daggruppe>,
    // Summen av beloepForventetRefusjon i daggruppene. Begrenset til 6G og justert for sykdomsgrad.
    val beloepForventetRefusjon: Int,
    // Summen av beloepUtbetaltTilArbeidsgiver i daggruppene. Beregnet av Spleis, som begrenser til 6G og justerer for sykdomsgrad.
    val beloepUtbetaltTilArbeidsgiver: Int,
    val dagerIgjenMedRettTilSykepenger: Int,
    val arbeidstakerVedtakMelding: ArbeidstakerVedtakMelding,
)

@Serializable
enum class Utfall {
    AVSLAG,
    DELVIS_INNVILGELSE,
    INNVILGELSE,
}
