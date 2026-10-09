@file:UseSerializers(UuidSerializer::class, LocalDateSerializer::class, LocalDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.vedtak

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

@Serializable
data class RefusjonUtfall(
    val refusjonUtfallId: UUID,
    val vedtaksperiodeId: UUID,
    val fnr: Fnr,
    val orgnr: Orgnr,
    val fom: LocalDate,
    val tom: LocalDate,
    val sykepengegrunnlag: Double,
    val utfallTilArbeidsgiver: Utfall,
    val fattetTidspunkt: LocalDateTime,
    val journalpostId: String,
    val sykmeldtNavn: String? = null,
    val arbeidsgiverNavn: String? = null,
)

@Serializable
enum class Utfall {
    AVSLAG,
    DELVIS_INNVILGELSE,
    INNVILGELSE,
}

fun ArbeidstakerVedtakMelding.tilRefusjonUtfall(
    refusjonsutfallId: UUID,
    arbeidsgiverNavn: String,
    sykmeldtNavn: String,
    journalpostId: String,
): RefusjonUtfall =
    RefusjonUtfall(
        refusjonUtfallId = refusjonsutfallId,
        vedtaksperiodeId = vedtaksperiodeId,
        fnr = foedselsnummer,
        orgnr = organisasjonsnummer,
        fom = fom,
        tom = tom,
        sykepengegrunnlag = sykepengegrunnlag,
        utfallTilArbeidsgiver =
            when (vedtaksUtfallTilArbeidsgiver) {
                VedtaksUtfall.AVSLAG -> Utfall.AVSLAG
                VedtaksUtfall.DELVIS_INNVILGELSE -> Utfall.DELVIS_INNVILGELSE
                VedtaksUtfall.INNVILGELSE -> Utfall.INNVILGELSE
            },
        fattetTidspunkt = vedtakFattetTidspunkt,
        journalpostId = journalpostId,
        sykmeldtNavn = sykmeldtNavn,
        arbeidsgiverNavn = arbeidsgiverNavn,
    )
