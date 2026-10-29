package no.nav.helsearbeidsgiver.refusjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class RefusjonUtfallMapperTest :
    FunSpec({
        val fnr = Fnr.genererGyldig()
        val orgnr = Orgnr.genererGyldig()
        val refusjonUtfallId = UUID.randomUUID().toKotlinUuid()
        val imId = UUID.randomUUID().toKotlinUuid()

        fun utbetalingsdag(
            dato: LocalDate,
            imReferanse: Uuid? = imId,
        ): Utbetalingsdag = utbetalingsdagMock(dato = dato, hagInntektsmeldingReferanse = imReferanse)

        test("mapper toppnivåfelter fra vedtaket") {
            val melding = arbeidstakerVedtakMeldingMock(fnr, orgnr)

            melding.tilRefusjonUtfall(refusjonUtfallId, emptyList(), "Arbeidsgiver AS", "Ola Nordmann") shouldBe
                RefusjonUtfall(
                    refusjonUtfallId = refusjonUtfallId,
                    vedtaksperiodeId = melding.vedtaksperiodeId,
                    fnr = fnr,
                    orgnr = orgnr,
                    fom = melding.fom,
                    tom = melding.tom,
                    sykepengegrunnlag = melding.sykepengegrunnlag,
                    utfallTilArbeidsgiver = Utfall.INNVILGELSE,
                    arbeidstakervedtakFattetTidspunkt = melding.vedtakFattetTidspunkt,
                    sykmeldtNavn = "Ola Nordmann",
                    arbeidsgiverNavn = "Arbeidsgiver AS",
                    daggrupper = emptyList(),
                    beloepForventetRefusjon = 0,
                    beloepUtbetaltTilArbeidsgiver = 0,
                    dagerIgjenMedRettTilSykepenger = DAGER_IGJEN_MED_RETT_TIL_SYKEPENGER,
                    arbeidstakerVedtakMelding = melding,
                )
        }

        context("utfall til arbeidsgiver er alltid innvilgelse") {
            withData(VedtaksUtfall.entries) { vedtaksUtfall ->
                val refusjonUtfall =
                    arbeidstakerVedtakMeldingMock(fnr, orgnr)
                        .copy(vedtaksUtfallTilArbeidsgiver = vedtaksUtfall)
                        .tilRefusjonUtfall(refusjonUtfallId, emptyList(), "Arbeidsgiver AS", "Ola Nordmann")

                @Suppress("DEPRECATION")
                val utfallTilArbeidsgiver = refusjonUtfall.utfallTilArbeidsgiver

                utfallTilArbeidsgiver shouldBe Utfall.INNVILGELSE
            }
        }

        test("grupperer dagene og summerer beløpene fra daggruppene") {
            val melding =
                arbeidstakerVedtakMeldingMock(fnr, orgnr).copy(
                    utbetalingsdager =
                        listOf(
                            utbetalingsdagMock(dato = LocalDate.of(2026, 7, 31), hagInntektsmeldingReferanse = null),
                            utbetalingsdagMock(
                                dato = LocalDate.of(2026, 8, 1),
                                type = Utbetalingsdagtype.NAV_HELG_DAG,
                                beloepTilArbeidsgiver = 0,
                                hagInntektsmeldingReferanse = null,
                            ),
                            utbetalingsdagMock(dato = LocalDate.of(2026, 8, 3), hagInntektsmeldingReferanse = null),
                        ),
                )

            val refusjonUtfall = melding.tilRefusjonUtfall(refusjonUtfallId, emptyList(), "Arbeidsgiver AS", "Ola Nordmann")

            refusjonUtfall.beloepForventetRefusjon shouldBe 2000
            refusjonUtfall.beloepUtbetaltTilArbeidsgiver shouldBe 2000
            refusjonUtfall.daggrupper shouldBe
                listOf(
                    Daggruppe(
                        antallDager = 2,
                        sykepengeStatus = SykepengeStatus.RETT_TIL_SYKEPENGER,
                        sykdomsgrad = 100,
                        beloepKrevdRefusjonPerDag = 1000,
                        beloepKrevdRefusjonPerMaaned = null,
                        beloepForventetRefusjon = 2000,
                        beloepUtbetaltTilArbeidsgiver = 2000,
                        begrensetTil6G = false,
                    ),
                )
        }

        test("bruker anvendt 6G fra vedtaket") {
            // 1 000 kr per dag × 260 = 260 000 kr per år, som er over en anvendt 6G på 200 000 kr
            val melding =
                arbeidstakerVedtakMeldingMock(fnr, orgnr).copy(
                    anvendtSeksG = 200000.0,
                    utbetalingsdager = listOf(utbetalingsdagMock(hagInntektsmeldingReferanse = null)),
                )

            melding
                .tilRefusjonUtfall(refusjonUtfallId, emptyList(), "Arbeidsgiver AS", "Ola Nordmann")
                .daggrupper
                .single()
                .begrensetTil6G shouldBe true
        }

        test("henter distinkte inntektsmeldingreferanser fra utbetalingsdagene") {
            val annenImId = UUID.randomUUID().toKotlinUuid()
            val melding =
                arbeidstakerVedtakMeldingMock(fnr, orgnr).copy(
                    utbetalingsdager =
                        listOf(
                            utbetalingsdag(LocalDate.of(2026, 7, 28), imId),
                            utbetalingsdag(LocalDate.of(2026, 7, 29), imId),
                            utbetalingsdag(LocalDate.of(2026, 7, 30), null),
                            utbetalingsdag(LocalDate.of(2026, 7, 31), annenImId),
                        ),
                )

            melding.hagInntektsmeldingReferanser() shouldBe listOf(imId, annenImId)
        }
    })
