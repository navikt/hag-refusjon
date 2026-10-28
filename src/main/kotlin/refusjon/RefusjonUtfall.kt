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
    val sykepengegrunnlag: Double,
    @Deprecated("Er alltid INNVILGELSE og skal fjernes. Beholdes fordi lps-api krever feltet.")
    val utfallTilArbeidsgiver: Utfall,
    val arbeidstakervedtakFattetTidspunkt: LocalDateTime,
    val sykmeldtNavn: String,
    val arbeidsgiverNavn: String,
    val daggrupper: List<Daggruppe>,
    val beloepKrevdAvArbeidsgiver: Int,
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

@Serializable
data class Daggruppe(
    val antallDager: Int,
    val sykepengeStatus: SykepengeStatus,
    val sykdomsgrad: Int,
    val beloepKrevdRefusjon: Int,
    val beloepKrevdRefusjonPerDag: Int,
    val beloepKrevdRefusjonPerMaaned: Double?,
    val beloepUtbetaltTilArbeidsgiver: Int,
    val begrensetTil6G: Boolean,
)

@Serializable
enum class SykepengeStatus {
    RETT_TIL_SYKEPENGER,
    RETT_TIL_SYKEPENGER_DIFFERANSE,
    IKKE_RETT_TIL_SYKEPENGER,
}
