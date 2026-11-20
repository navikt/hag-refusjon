package no.nav.helsearbeidsgiver.refusjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

class ArbeidstakerVedtakValidatorTest :
    FunSpec({
        val melding =
            arbeidstakerVedtakMeldingMock(Fnr.genererGyldig(), Orgnr.genererGyldig()).let {
                it.copy(utbetalingsdager = listOf(utbetalingsdagMock(dato = it.fom)))
            }

        test("godtar gyldig vedtak") {
            ArbeidstakerVedtakValidator
                .valider(
                    melding.copy(
                        utbetalingsdager =
                            listOf(
                                utbetalingsdagMock(dato = melding.fom, beloepTilArbeidsgiver = 1000),
                                utbetalingsdagMock(
                                    dato = melding.fom.plusDays(1),
                                    type = Utbetalingsdagtype.UKJENT_DAG,
                                    beloepTilArbeidsgiver = 0,
                                ),
                            ),
                    ),
                ).shouldBeEmpty()
        }

        test("krever minst én sykmelding") {
            ArbeidstakerVedtakValidator
                .valider(melding.copy(dokumenter = emptyList()))
                .shouldContainExactly(ArbeidstakerVedtakValideringFeil.MANGLER_SYKMELDING)
        }

        test("avviser negativt beløp til arbeidsgiver") {
            val ugyldig = utbetalingsdagMock(beloepTilArbeidsgiver = -1)

            ArbeidstakerVedtakValidator
                .valider(melding.copy(utbetalingsdager = listOf(ugyldig)))
                .shouldContainExactly(ArbeidstakerVedtakValideringFeil.NEGATIVT_BELOEP_TIL_ARBEIDSGIVER)
        }

        test("avviser positivt beløp til arbeidsgiver på dagtyper der vi ikke forventer det") {
            val ugyldig = utbetalingsdagMock(type = Utbetalingsdagtype.AVVIST_DAG)

            ArbeidstakerVedtakValidator
                .valider(melding.copy(utbetalingsdager = listOf(ugyldig)))
                .shouldContainExactly(ArbeidstakerVedtakValideringFeil.UVENTET_UTBETALING_TIL_ARBEIDSGIVER_PAA_DAGTYPE)
        }

        test("avviser vedtaksperiode med fom etter tom") {
            // Med fom etter tom havner alle utbetalingsdager utenfor perioden.
            ArbeidstakerVedtakValidator
                .valider(melding.copy(fom = melding.tom.plusDays(1)))
                .shouldContainExactlyInAnyOrder(
                    ArbeidstakerVedtakValideringFeil.UGYLDIG_PERIODE,
                    ArbeidstakerVedtakValideringFeil.UTBETALINGSDAG_UTENFOR_VEDTAKSPERIODE,
                )
        }

        test("godtar vedtaksperiode på én dag") {
            ArbeidstakerVedtakValidator
                .valider(melding.copy(tom = melding.fom))
                .shouldBeEmpty()
        }

        context("avviser anvendt 6G som ikke er større enn 0") {
            withData(0.0, -1.0) { anvendtSeksG ->
                ArbeidstakerVedtakValidator
                    .valider(melding.copy(anvendtSeksG = anvendtSeksG))
                    .shouldContainExactly(ArbeidstakerVedtakValideringFeil.UGYLDIG_ANVENDT_SEKS_G)
            }
        }

        test("avviser negativt sykepengegrunnlag") {
            ArbeidstakerVedtakValidator
                .valider(melding.copy(sykepengegrunnlag = -0.01))
                .shouldContainExactly(ArbeidstakerVedtakValideringFeil.NEGATIVT_SYKEPENGEGRUNNLAG)
        }

        test("godtar sykepengegrunnlag på 0") {
            ArbeidstakerVedtakValidator
                .valider(melding.copy(sykepengegrunnlag = 0.0))
                .shouldBeEmpty()
        }

        test("krever minst én utbetalingsdag") {
            ArbeidstakerVedtakValidator
                .valider(melding.copy(utbetalingsdager = emptyList()))
                .shouldContainExactly(ArbeidstakerVedtakValideringFeil.MANGLER_UTBETALINGSDAGER)
        }

        test("avviser utbetalingsdag utenfor vedtaksperioden") {
            val ugyldig = utbetalingsdagMock(dato = melding.fom.minusDays(1))

            ArbeidstakerVedtakValidator
                .valider(melding.copy(utbetalingsdager = listOf(ugyldig)))
                .shouldContainExactly(ArbeidstakerVedtakValideringFeil.UTBETALINGSDAG_UTENFOR_VEDTAKSPERIODE)
        }

        test("avviser duplikate utbetalingsdatoer") {
            val dag = utbetalingsdagMock(dato = melding.fom)

            ArbeidstakerVedtakValidator
                .valider(melding.copy(utbetalingsdager = listOf(dag, dag.copy())))
                .shouldContainExactly(ArbeidstakerVedtakValideringFeil.DUPLIKAT_UTBETALINGSDATO)
        }

        context("avviser sykdomsgrad utenfor 0 til 100 på dager som kan inngå i en daggruppe") {
            withData(-1, 101) { sykdomsgrad ->
                val ugyldig = utbetalingsdagMock(sykdomsgrad = sykdomsgrad)

                ArbeidstakerVedtakValidator
                    .valider(melding.copy(utbetalingsdager = listOf(ugyldig)))
                    .shouldContainExactly(ArbeidstakerVedtakValideringFeil.UGYLDIG_SYKDOMSGRAD)
            }
        }

        test("godtar sykdomsgrad utenfor 0 til 100 på dager utenfor daggruppene") {
            val dag = utbetalingsdagMock(type = Utbetalingsdagtype.FERIEDAG, sykdomsgrad = 101, beloepTilArbeidsgiver = 0)

            ArbeidstakerVedtakValidator
                .valider(melding.copy(utbetalingsdager = listOf(dag)))
                .shouldBeEmpty()
        }
    })
