package no.nav.helsearbeidsgiver.refusjon

import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

fun arbeidstakerVedtakMeldingMock(
    fnr: Fnr,
    orgnr: Orgnr,
): ArbeidstakerVedtakMelding =
    ArbeidstakerVedtakMelding(
        eventName = SisEventName.VEDTAK_FATTET,
        foedselsnummer = fnr,
        yrkesaktivitetstype = Yrkesaktivitetstype.ARBEIDSTAKER,
        organisasjonsnummer = orgnr,
        vedtaksperiodeId = UUID.randomUUID().toKotlinUuid(),
        behandlingId = UUID.randomUUID().toKotlinUuid(),
        fom = LocalDate.of(2026, 7, 28),
        tom = LocalDate.of(2026, 8, 3),
        skjaeringstidspunkt = LocalDate.of(2026, 7, 1),
        dokumenter = emptyList(),
        sykepengegrunnlag = 154999.92,
        vedtakFattetTidspunkt = LocalDateTime.of(2026, 8, 5, 13, 3, 25),
        vedtaksUtfallTilArbeidsgiver = VedtaksUtfall.INNVILGELSE,
        saksbehandlerIdent = null,
        saksbehandlerNavn = null,
        beslutterIdent = null,
        beslutterNavn = null,
        automatiskFattet = true,
        harArbeidsgiverOensketRefusjon = true,
    )

fun utbetalingsdagMock(
    dato: LocalDate = LocalDate.of(2026, 7, 28),
    type: Utbetalingsdagtype = Utbetalingsdagtype.NAV_DAG,
    avsender: Avsender = Avsender.ARBEIDSGIVER,
    sykdomsgrad: Int = 100,
    beloepTilArbeidsgiver: Int = 1000,
    forespurtRefusjonBeloep: Int = 1000,
    hagInntektsmeldingReferanse: Uuid? = UUID.randomUUID().toKotlinUuid(),
): Utbetalingsdag =
    Utbetalingsdag(
        dato = dato,
        type = type,
        sykdomsgrad = sykdomsgrad,
        dekningsgrad = 100,
        beloepTilBruker = 0,
        beloepTilArbeidsgiver = beloepTilArbeidsgiver,
        forespurtRefusjon =
            ForespurtRefusjon(
                beloep = forespurtRefusjonBeloep,
                avsender = avsender,
                sasInntektsmeldingReferanse = null,
                hagInntektsmeldingReferanse = hagInntektsmeldingReferanse,
            ),
        begrunnelser = emptyList(),
    )
