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
    val utfallTilArbeidsgiver: Utfall,
    val fattetTidspunkt: LocalDateTime,
    val sykmeldtNavn: String,
    val arbeidsgiverNavn: String,
    val dager: List<Dag>,
    val arbeidstakerVedtakMelding: ArbeidstakerVedtakMelding,
)

@Serializable
enum class Utfall {
    AVSLAG,
    DELVIS_INNVILGELSE,
    INNVILGELSE,
}

@Serializable
data class Dag(
    val dato: LocalDate,
    val dagtype: DagType,
    val beloepUtbetaltTilArbeidsgiver: Int,
    val beloepKrevdRefusjonPerDag: Int,
    val beloepKrevdRefusjonPerMaaned: Double?,
)

@Serializable
enum class DagType {
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
