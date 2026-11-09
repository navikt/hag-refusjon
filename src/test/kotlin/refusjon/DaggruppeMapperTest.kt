package no.nav.helsearbeidsgiver.refusjon

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

private const val MAANEDER_PER_AAR = 12
private const val VIRKEDAGER_PER_AAR = 260

private const val REFUSJONSKRAV_PER_MAANED = 26000.0
private val DAGSATS = (REFUSJONSKRAV_PER_MAANED * MAANEDER_PER_AAR / VIRKEDAGER_PER_AAR).toInt()

private const val REFUSJONSKRAV_OVER_6G_PER_MAANED = 80000.0
private val DAGSATS_6G = ANVENDT_6G.divide(VIRKEDAGER_PER_AAR.toBigDecimal(), 0, RoundingMode.DOWN).toInt()

private val IM_ID = UUID.randomUUID().toKotlinUuid()
private val IM_ID_OVER_6G = UUID.randomUUID().toKotlinUuid()
private val IM_ID_UTEN_KRAV = UUID.randomUUID().toKotlinUuid()

private val REFUSJONSKRAV: Map<Uuid, Refusjon?> =
    mapOf(
        IM_ID to Refusjon(beloepPerMaaned = REFUSJONSKRAV_PER_MAANED, endringer = emptyList()),
        IM_ID_OVER_6G to Refusjon(beloepPerMaaned = REFUSJONSKRAV_OVER_6G_PER_MAANED, endringer = emptyList()),
        IM_ID_UTEN_KRAV to null,
    )

private val DAGTYPER_UTENFOR_DAGGRUPPER =
    listOf(
        Utbetalingsdagtype.NAV_HELG_DAG,
        Utbetalingsdagtype.FRIDAG,
        Utbetalingsdagtype.FERIEDAG,
        Utbetalingsdagtype.PERMISJONSDAG,
        Utbetalingsdagtype.ARBEIDSDAG,
        Utbetalingsdagtype.VENTETIDSDAG,
        Utbetalingsdagtype.ARBEID_IKKE_GJENOPPTATT_DAG,
        Utbetalingsdagtype.ANDRE_YTELSER,
        Utbetalingsdagtype.UKJENT_DAG,
    )

class DaggruppeMapperTest :
    FunSpec({
        context("daggrupper") {
            test("ingen dager gir ingen daggrupper") {
                daggrupper().shouldBeEmpty()
            }

            test("Nav-dager har rett til sykepenger") {
                daggrupper(dag(august(3))).map { it.sykepengeStatus } shouldBe listOf(SykepengeStatus.RETT_TIL_SYKEPENGER)
            }

            context("dager uten rett til sykepenger") {
                withData(Utbetalingsdagtype.AVVIST_DAG, Utbetalingsdagtype.FORELDET_DAG) { type ->
                    daggrupper(dag(august(3), type)).map { it.sykepengeStatus } shouldBe listOf(SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER)
                }
            }

            context("tar ikke med dager utenfor daggruppene") {
                withData(DAGTYPER_UTENFOR_DAGGRUPPER) { type ->
                    daggrupper(dag(august(3), type)).shouldBeEmpty()
                }
            }

            context("arbeidsgiverperiodedager er Nav-dager bare når Nav utbetaler til arbeidsgiver") {
                withData(
                    nameFn = { (type, beloep, forventet) -> "$type med $beloep kr til arbeidsgiver gir $forventet" },
                    Triple(Utbetalingsdagtype.ARBEIDSGIVERPERIODE_DAG, DAGSATS, listOf(SykepengeStatus.RETT_TIL_SYKEPENGER)),
                    Triple(Utbetalingsdagtype.ARBEIDSGIVERPERIODE_DAG, 0, emptyList()),
                    Triple(Utbetalingsdagtype.ARBEIDSGIVERPERIODEDAG_NAV, DAGSATS, listOf(SykepengeStatus.RETT_TIL_SYKEPENGER)),
                    Triple(Utbetalingsdagtype.ARBEIDSGIVERPERIODEDAG_NAV, 0, emptyList()),
                ) { (type, beloep, forventet) ->
                    daggrupper(dag(august(3), type, beloepTilArbeidsgiver = beloep)).map { it.sykepengeStatus } shouldBe forventet
                }
            }

            context("feiler ved utbetaling til arbeidsgiver på dager utenfor daggruppene") {
                withData(DAGTYPER_UTENFOR_DAGGRUPPER) { type ->
                    shouldThrow<IllegalStateException> {
                        daggrupper(dag(august(3), type, beloepTilArbeidsgiver = 1))
                    }
                }
            }

            test("slår sammen påfølgende like dager til én daggruppe") {
                daggrupper(
                    dag(august(3)),
                    dag(august(4)),
                    dag(august(5)),
                ) shouldBe listOf(daggruppe(antallDager = 3))
            }

            test("helg bryter ikke en daggruppe og telles ikke med i antall dager") {
                val toUkerMedHelg =
                    (3..14).map { august(it) }.map { dato ->
                        if (dato.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                            dag(dato, Utbetalingsdagtype.NAV_HELG_DAG)
                        } else {
                            dag(dato)
                        }
                    }

                daggrupper(*toUkerMedHelg.toTypedArray()) shouldBe listOf(daggruppe(antallDager = 10))
            }

            test("sorterer dagene før de slås sammen") {
                daggrupper(
                    dag(august(4), sykdomsgrad = 50),
                    dag(august(3)),
                    dag(august(5)),
                ) shouldBe
                    listOf(
                        daggruppe(antallDager = 1),
                        daggruppe(antallDager = 1, sykdomsgrad = 50),
                        daggruppe(antallDager = 1),
                    )
            }

            test("endret refusjonskrav bryter ikke en daggruppe når utbetalingen er lik") {
                daggrupper(
                    dag(august(3)),
                    dag(august(4), imReferanse = null),
                    dag(august(5), imReferanse = IM_ID_UTEN_KRAV),
                ) shouldBe listOf(daggruppe(antallDager = 3))
            }

            context("starter ny daggruppe når") {
                withData(
                    nameFn = { it.first },
                    "status endres" to dag(august(4), Utbetalingsdagtype.AVVIST_DAG),
                    "sykdomsgrad endres" to dag(august(4), sykdomsgrad = 50, beloepTilArbeidsgiver = DAGSATS),
                    "beløp utbetalt per dag endres" to dag(august(4), beloepTilArbeidsgiver = DAGSATS + 10),
                ) { (_, endretDag) ->
                    daggrupper(dag(august(3)), endretDag) shouldHaveSize 2
                }
            }

            test("grupperer dager uten rett til sykepenger etter samme regler") {
                daggrupper(
                    dag(august(3), Utbetalingsdagtype.AVVIST_DAG, sykdomsgrad = 10),
                    dag(august(4), Utbetalingsdagtype.AVVIST_DAG, sykdomsgrad = 10),
                    dag(august(5), Utbetalingsdagtype.AVVIST_DAG, sykdomsgrad = 15),
                ) shouldBe
                    listOf(
                        daggruppe(antallDager = 2, beloepPerDag = 0, status = SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER, sykdomsgrad = 10),
                        daggruppe(antallDager = 1, beloepPerDag = 0, status = SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER, sykdomsgrad = 15),
                    )
            }
        }

        context("sykepengestatus") {
            withData(
                nameFn = { (utbetalt, status) -> "utbetalt $utbetalt kr mot forventet $DAGSATS kr gir $status" },
                DAGSATS to SykepengeStatus.RETT_TIL_SYKEPENGER,
                DAGSATS - 1 to SykepengeStatus.RETT_TIL_SYKEPENGER,
                DAGSATS + 1 to SykepengeStatus.RETT_TIL_SYKEPENGER,
                DAGSATS + 2 to SykepengeStatus.RETT_TIL_SYKEPENGER,
                DAGSATS * 2 to SykepengeStatus.RETT_TIL_SYKEPENGER,
                DAGSATS - 2 to SykepengeStatus.RETT_TIL_SYKEPENGER_DIFFERANSE,
            ) { (utbetalt, forventetStatus) ->
                daggrupper(dag(august(3), beloepTilArbeidsgiver = utbetalt)).single().sykepengeStatus shouldBe forventetStatus
            }

            test("tar høyde for sykdomsgrad i forventet refusjon") {
                daggrupper(
                    dag(august(3), sykdomsgrad = 50, beloepTilArbeidsgiver = DAGSATS / 2),
                    dag(august(4), sykdomsgrad = 50, beloepTilArbeidsgiver = DAGSATS / 2 - 2),
                ).map { it.sykepengeStatus } shouldBe
                    listOf(SykepengeStatus.RETT_TIL_SYKEPENGER, SykepengeStatus.RETT_TIL_SYKEPENGER_DIFFERANSE)
            }

            context("refusjonskrav over 6G") {
                test("utbetalt 6G-dagsats gir ikke differanse") {
                    daggrupper(dag(august(3), beloepTilArbeidsgiver = DAGSATS_6G, imReferanse = IM_ID_OVER_6G))
                        .single()
                        .sykepengeStatus shouldBe SykepengeStatus.RETT_TIL_SYKEPENGER
                }

                test("utbetaling lavere enn 6G-dagsatsen gir differanse") {
                    daggrupper(dag(august(3), beloepTilArbeidsgiver = DAGSATS_6G - 2, imReferanse = IM_ID_OVER_6G))
                        .single()
                        .sykepengeStatus shouldBe SykepengeStatus.RETT_TIL_SYKEPENGER_DIFFERANSE
                }
            }

            context("gir aldri differanse uten refusjonskrav fra inntektsmelding") {
                withData(
                    nameFn = { it.first },
                    "dagen mangler referanse til inntektsmelding" to null,
                    "inntektsmeldingen finnes ikke blant refusjonskravene" to UUID.randomUUID().toKotlinUuid(),
                    "inntektsmeldingen har ikke refusjonskrav" to IM_ID_UTEN_KRAV,
                ) { (_, imReferanse) ->
                    daggrupper(dag(august(3), beloepTilArbeidsgiver = 1, imReferanse = imReferanse))
                        .single()
                        .sykepengeStatus shouldBe SykepengeStatus.RETT_TIL_SYKEPENGER
                }
            }
        }
    })

private fun august(dag: Int): LocalDate = LocalDate.of(2026, 8, dag)

private fun dag(
    dato: LocalDate,
    type: Utbetalingsdagtype = Utbetalingsdagtype.NAV_DAG,
    sykdomsgrad: Int = 100,
    beloepTilArbeidsgiver: Int = if (type == Utbetalingsdagtype.NAV_DAG) DAGSATS * sykdomsgrad / 100 else 0,
    imReferanse: Uuid? = IM_ID,
): Utbetalingsdag =
    utbetalingsdagMock(
        dato = dato,
        type = type,
        sykdomsgrad = sykdomsgrad,
        beloepTilArbeidsgiver = beloepTilArbeidsgiver,
        hagInntektsmeldingReferanse = imReferanse,
    )

private fun daggruppe(
    antallDager: Int,
    status: SykepengeStatus = SykepengeStatus.RETT_TIL_SYKEPENGER,
    sykdomsgrad: Int = 100,
    beloepPerDag: Int = DAGSATS * sykdomsgrad / 100,
): Daggruppe =
    Daggruppe(
        antallDager = antallDager,
        sykepengeStatus = status,
        sykdomsgrad = sykdomsgrad,
        beloepUtbetaltTilArbeidsgiverPerDag = beloepPerDag,
        beloepUtbetaltTilArbeidsgiver = antallDager * beloepPerDag,
    )

private fun daggrupper(vararg dager: Utbetalingsdag): List<Daggruppe> = dager.toList().tilDaggrupper(REFUSJONSKRAV, ANVENDT_6G)
