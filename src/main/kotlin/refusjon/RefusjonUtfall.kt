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

@Serializable
data class Daggruppe(
    val antallDager: Int,
    val sykepengeStatus: SykepengeStatus,
    val sykdomsgrad: Int,
    // Forventet refusjon per dag, summert for gruppen. Bruker refusjonskravet per måned fra inntektsmeldingen × 12,
    // ellers refusjonskravet per dag fra sis × 260. Begrenset til 6G, justert for sykdomsgrad og rundet til hele kroner per dag.
    val beloepForventetRefusjon: Int,
    // Refusjonskrav per dag fra sis (forespurtRefusjon), uendret. Ikke begrenset til 6G eller justert for sykdomsgrad.
    val beloepKrevdRefusjonPerDag: Int,
    // Refusjonskrav per måned fra inntektsmeldingen som gjelder på datoen, uendret. Null hvis vedtaket ikke peker på en inntektsmelding.
    val beloepKrevdRefusjonPerMaaned: Double?,
    // Summen av beløp til arbeidsgiver fra sis for dagene i gruppen. Beregnet av Spleis, som begrenser til 6G og justerer for sykdomsgrad.
    val beloepUtbetaltTilArbeidsgiver: Int,
    val begrensetTil6G: Boolean,
)

@Serializable
enum class SykepengeStatus {
    RETT_TIL_SYKEPENGER,
    RETT_TIL_SYKEPENGER_DIFFERANSE,
    IKKE_RETT_TIL_SYKEPENGER,
}
