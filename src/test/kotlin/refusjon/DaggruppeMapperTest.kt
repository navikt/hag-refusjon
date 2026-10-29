package no.nav.helsearbeidsgiver.refusjon

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate

private const val MAANEDER_PER_AAR = 12
private const val VIRKEDAGER_PER_AAR = 260

private const val REFUSJONSKRAV_PER_MAANED = 26000.0
private val DAGSATS = (REFUSJONSKRAV_PER_MAANED * MAANEDER_PER_AAR / VIRKEDAGER_PER_AAR).toInt()

private const val REFUSJONSKRAV_OVER_6G_PER_MAANED = 80000.0
private val DAGSATS_6G = ANVENDT_6G.divide(VIRKEDAGER_PER_AAR.toBigDecimal(), 0, RoundingMode.DOWN).toInt()

class DaggruppeMapperTest :
    FunSpec({
        context("daggrupper") {
            test("ingen dager gir ingen daggrupper") {
                daggrupper().shouldBeEmpty()
            }

            context("tar bare med dager med eller uten rett til sykepenger") {
                withData(
                    Dagtype.NAV_DAG to SykepengeStatus.RETT_TIL_SYKEPENGER,
                    Dagtype.IKKE_RETT_PAA_SYKEPENGER to SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER,
                    Dagtype.ARBEIDSGIVERPERIODE to null,
                    Dagtype.HELG to null,
                    Dagtype.FRI to null,
                    Dagtype.FERIE to null,
                    Dagtype.PERMISJON to null,
                    Dagtype.ARBEIDSDAG to null,
                    Dagtype.UKJENT to null,
                ) { (dagtype, forventetStatus) ->
                    daggrupper(dag(august(3), dagtype)).map { it.sykepengeStatus } shouldBe listOfNotNull(forventetStatus)
                }
            }

            context("feiler ved utbetaling til arbeidsgiver på dager utenfor daggruppene") {
                withData(
                    Dagtype.ARBEIDSGIVERPERIODE,
                    Dagtype.HELG,
                    Dagtype.FRI,
                    Dagtype.FERIE,
                    Dagtype.PERMISJON,
                    Dagtype.ARBEIDSDAG,
                    Dagtype.UKJENT,
                ) { dagtype ->
                    shouldThrow<IllegalStateException> {
                        daggrupper(dag(august(3), dagtype, beloepUtbetaltTilArbeidsgiver = 1))
                    }
                }
            }

            test("slår sammen påfølgende like dager til én daggruppe") {
                daggrupper(
                    dag(august(3)),
                    dag(august(4)),
                    dag(august(5)),
                ) shouldBe listOf(daggruppe(antallDager = 3, beloepUtbetaltTilArbeidsgiver = 3 * DAGSATS))
            }

            test("helg bryter ikke en daggruppe og telles ikke med i antall dager") {
                val toUkerMedHelg =
                    (3..14).map { august(it) }.map { dato ->
                        if (dato.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                            dag(dato, Dagtype.HELG, beloepUtbetaltTilArbeidsgiver = 0)
                        } else {
                            dag(dato)
                        }
                    }

                daggrupper(*toUkerMedHelg.toTypedArray()) shouldBe
                    listOf(daggruppe(antallDager = 10, beloepUtbetaltTilArbeidsgiver = 10 * DAGSATS))
            }

            test("sorterer dagene før de slås sammen") {
                daggrupper(
                    dag(august(4), sykdomsgrad = 50),
                    dag(august(3)),
                    dag(august(5)),
                ) shouldBe
                    listOf(
                        daggruppe(antallDager = 1, beloepUtbetaltTilArbeidsgiver = DAGSATS),
                        daggruppe(antallDager = 1, beloepUtbetaltTilArbeidsgiver = DAGSATS / 2, sykdomsgrad = 50),
                        daggruppe(antallDager = 1, beloepUtbetaltTilArbeidsgiver = DAGSATS),
                    )
            }

            test("summerer beløp utbetalt til arbeidsgiver i daggruppen") {
                daggrupper(
                    dag(august(3), beloepUtbetaltTilArbeidsgiver = DAGSATS),
                    dag(august(4), beloepUtbetaltTilArbeidsgiver = DAGSATS - 1),
                ) shouldBe listOf(daggruppe(antallDager = 2, beloepUtbetaltTilArbeidsgiver = 2 * DAGSATS - 1))
            }

            test("summerer forventet refusjon justert for sykdomsgrad, også for dager uten rett til sykepenger") {
                daggrupper(
                    dag(august(3), sykdomsgrad = 50),
                    dag(august(4), sykdomsgrad = 50),
                    dag(august(5), Dagtype.IKKE_RETT_PAA_SYKEPENGER, beloepUtbetaltTilArbeidsgiver = 0),
                    dag(august(6), Dagtype.IKKE_RETT_PAA_SYKEPENGER, beloepUtbetaltTilArbeidsgiver = 0),
                ).map { it.beloepForventetRefusjon } shouldBe listOf(2 * (DAGSATS / 2), 2 * DAGSATS)
            }

            test("forventet refusjon begrenses til 6G") {
                fun dagOverSeksG(dato: LocalDate) =
                    dag(dato, beloepKrevdRefusjonPerMaaned = REFUSJONSKRAV_OVER_6G_PER_MAANED, beloepUtbetaltTilArbeidsgiver = DAGSATS_6G)

                daggrupper(dagOverSeksG(august(3)), dagOverSeksG(august(4))).single().beloepForventetRefusjon shouldBe 2 * DAGSATS_6G
            }

            context("starter ny daggruppe når") {
                withData(
                    nameFn = { it.first },
                    "status endres" to dag(august(4), Dagtype.IKKE_RETT_PAA_SYKEPENGER),
                    "sykdomsgrad endres" to dag(august(4), sykdomsgrad = 50),
                    "refusjonskrav per dag endres" to dag(august(4), beloepKrevdRefusjonPerDag = DAGSATS / 2),
                    "refusjonskrav per måned endres" to
                        dag(
                            august(4),
                            beloepKrevdRefusjonPerMaaned = REFUSJONSKRAV_PER_MAANED / 2,
                            beloepUtbetaltTilArbeidsgiver = DAGSATS / 2,
                        ),
                    "utbetalingen avviker fra forventet" to dag(august(4), beloepUtbetaltTilArbeidsgiver = DAGSATS / 2),
                ) { (_, endretDag) ->
                    daggrupper(dag(august(3)), endretDag) shouldHaveSize 2
                }
            }

            test("grupperer dager uten rett til sykepenger etter samme regler") {
                daggrupper(
                    dag(august(3), Dagtype.IKKE_RETT_PAA_SYKEPENGER, sykdomsgrad = 10, beloepUtbetaltTilArbeidsgiver = 0),
                    dag(august(4), Dagtype.IKKE_RETT_PAA_SYKEPENGER, sykdomsgrad = 10, beloepUtbetaltTilArbeidsgiver = 0),
                    dag(august(5), Dagtype.IKKE_RETT_PAA_SYKEPENGER, sykdomsgrad = 15, beloepUtbetaltTilArbeidsgiver = 0),
                ) shouldBe
                    listOf(
                        daggruppe(
                            antallDager = 2,
                            beloepUtbetaltTilArbeidsgiver = 0,
                            status = SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER,
                            sykdomsgrad = 10,
                        ),
                        daggruppe(
                            antallDager = 1,
                            beloepUtbetaltTilArbeidsgiver = 0,
                            status = SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER,
                            sykdomsgrad = 15,
                        ),
                    )
            }
        }

        context("sykepengestatus") {
            withData(
                nameFn = { (utbetalt, status) -> "utbetalt $utbetalt kr mot forventet $DAGSATS kr gir $status" },
                DAGSATS to SykepengeStatus.RETT_TIL_SYKEPENGER,
                DAGSATS - 1 to SykepengeStatus.RETT_TIL_SYKEPENGER,
                DAGSATS + 1 to SykepengeStatus.RETT_TIL_SYKEPENGER,
                DAGSATS - 2 to SykepengeStatus.RETT_TIL_SYKEPENGER_DIFFERANSE,
                DAGSATS + 2 to SykepengeStatus.RETT_TIL_SYKEPENGER_DIFFERANSE,
            ) { (utbetalt, forventetStatus) ->
                daggrupper(dag(august(3), beloepUtbetaltTilArbeidsgiver = utbetalt)).single().sykepengeStatus shouldBe forventetStatus
            }

            test("tar høyde for sykdomsgrad i forventet refusjon") {
                daggrupper(
                    dag(august(3), sykdomsgrad = 50, beloepUtbetaltTilArbeidsgiver = DAGSATS / 2),
                    dag(august(4), sykdomsgrad = 50, beloepUtbetaltTilArbeidsgiver = DAGSATS / 2 - 2),
                ).map { it.sykepengeStatus } shouldBe
                    listOf(SykepengeStatus.RETT_TIL_SYKEPENGER, SykepengeStatus.RETT_TIL_SYKEPENGER_DIFFERANSE)
            }

            test("bruker refusjonskrav per dag fra sis når refusjonskrav per måned mangler") {
                fun dagUtenKravPerMaaned(
                    dato: LocalDate,
                    utbetalt: Int,
                ) = dag(
                    dato,
                    beloepKrevdRefusjonPerDag = 1000,
                    beloepKrevdRefusjonPerMaaned = null,
                    beloepUtbetaltTilArbeidsgiver = utbetalt,
                )

                daggrupper(
                    dagUtenKravPerMaaned(august(3), utbetalt = 1000),
                    dagUtenKravPerMaaned(august(4), utbetalt = 900),
                ).map { it.sykepengeStatus } shouldBe
                    listOf(SykepengeStatus.RETT_TIL_SYKEPENGER, SykepengeStatus.RETT_TIL_SYKEPENGER_DIFFERANSE)
            }

            test("dager uten rett til sykepenger vurderes ikke for differanse i refusjon eller 6G") {
                daggrupper(
                    dag(
                        august(3),
                        Dagtype.IKKE_RETT_PAA_SYKEPENGER,
                        beloepUtbetaltTilArbeidsgiver = 0,
                        beloepKrevdRefusjonPerMaaned = REFUSJONSKRAV_OVER_6G_PER_MAANED,
                    ),
                ).single() shouldBe
                    daggruppe(
                        antallDager = 1,
                        beloepUtbetaltTilArbeidsgiver = 0,
                        status = SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER,
                        beloepKrevdRefusjonPerMaaned = REFUSJONSKRAV_OVER_6G_PER_MAANED,
                        beloepForventetRefusjon = DAGSATS_6G,
                        begrensetTilSeksG = false,
                    )
            }
        }

        context("begrensetTilSeksG") {
            fun dagOverSeksG(utbetalt: Int) =
                dag(august(3), beloepKrevdRefusjonPerMaaned = REFUSJONSKRAV_OVER_6G_PER_MAANED, beloepUtbetaltTilArbeidsgiver = utbetalt)

            test("refusjonskrav over 6G er begrenset, og utbetalt 6G-dagsats gir ikke differanse") {
                daggrupper(dagOverSeksG(utbetalt = DAGSATS_6G)).single() shouldBe
                    daggruppe(
                        antallDager = 1,
                        beloepUtbetaltTilArbeidsgiver = DAGSATS_6G,
                        beloepKrevdRefusjonPerMaaned = REFUSJONSKRAV_OVER_6G_PER_MAANED,
                        beloepForventetRefusjon = DAGSATS_6G,
                        begrensetTilSeksG = true,
                    )
            }

            test("refusjonskrav over 6G gir differanse når utbetalingen avviker fra 6G-dagsatsen") {
                daggrupper(dagOverSeksG(utbetalt = DAGSATS_6G - 2)).single().sykepengeStatus shouldBe
                    SykepengeStatus.RETT_TIL_SYKEPENGER_DIFFERANSE
            }

            test("refusjonskrav på nøyaktig 6G er ikke begrenset") {
                daggrupper(
                    dag(
                        august(3),
                        beloepKrevdRefusjonPerMaaned = ANVENDT_6G.toDouble() / MAANEDER_PER_AAR,
                        beloepUtbetaltTilArbeidsgiver = DAGSATS_6G,
                    ),
                ).single().begrensetTilSeksG shouldBe false
            }

            test("refusjonskrav under 6G er ikke begrenset") {
                daggrupper(dag(august(3))).single().begrensetTilSeksG shouldBe false
            }
        }
    })

private fun august(dag: Int): LocalDate = LocalDate.of(2026, 8, dag)

private fun dag(
    dato: LocalDate,
    dagtype: Dagtype = Dagtype.NAV_DAG,
    sykdomsgrad: Int = 100,
    beloepUtbetaltTilArbeidsgiver: Int = if (dagtype == Dagtype.NAV_DAG) DAGSATS * sykdomsgrad / 100 else 0,
    beloepKrevdRefusjonPerDag: Int = DAGSATS,
    beloepKrevdRefusjonPerMaaned: Double? = REFUSJONSKRAV_PER_MAANED,
): Dag =
    Dag(
        dato = dato,
        dagtype = dagtype,
        sykdomsgrad = sykdomsgrad,
        beloepUtbetaltTilArbeidsgiver = beloepUtbetaltTilArbeidsgiver,
        beloepKrevdRefusjonPerDag = beloepKrevdRefusjonPerDag,
        beloepKrevdRefusjonPerMaaned = beloepKrevdRefusjonPerMaaned,
    )

private fun daggruppe(
    antallDager: Int,
    beloepUtbetaltTilArbeidsgiver: Int,
    status: SykepengeStatus = SykepengeStatus.RETT_TIL_SYKEPENGER,
    sykdomsgrad: Int = 100,
    beloepKrevdRefusjonPerDag: Int = DAGSATS,
    beloepKrevdRefusjonPerMaaned: Double? = REFUSJONSKRAV_PER_MAANED,
    beloepForventetRefusjon: Int = antallDager * DAGSATS * sykdomsgrad / 100,
    begrensetTilSeksG: Boolean = false,
): Daggruppe =
    Daggruppe(
        antallDager = antallDager,
        sykepengeStatus = status,
        sykdomsgrad = sykdomsgrad,
        beloepKrevdRefusjonPerDag = beloepKrevdRefusjonPerDag,
        beloepKrevdRefusjonPerMaaned = beloepKrevdRefusjonPerMaaned,
        beloepForventetRefusjon = beloepForventetRefusjon,
        beloepUtbetaltTilArbeidsgiver = beloepUtbetaltTilArbeidsgiver,
        begrensetTilSeksG = begrensetTilSeksG,
    )

private fun daggrupper(vararg dager: Dag): List<Daggruppe> = dager.toList().tilDaggrupper(ANVENDT_6G)
