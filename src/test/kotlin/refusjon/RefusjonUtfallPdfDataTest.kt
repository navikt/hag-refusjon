package no.nav.helsearbeidsgiver.refusjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RefusjonEndring
import no.nav.helsearbeidsgiver.refusjonskrav.InntektsmeldingRefusjonskrav
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlin.uuid.toKotlinUuid

private val IM_ID = UUID.randomUUID().toKotlinUuid()
private val REFUSJONSKRAV = Refusjon(beloepPerMaaned = 30000.0, endringer = emptyList())

class RefusjonUtfallPdfDataTest :
    FunSpec({
        context("daggrupper") {
            test("ingen utbetalingsdager gir ingen daggrupper") {
                daggrupper().shouldBeEmpty()
            }

            context("tar bare med dager med eller uten rett til sykepenger") {
                withData(
                    Utbetalingsdagtype.NAV_DAG to SykepengeStatus.RETT_TIL_SYKEPENGER,
                    Utbetalingsdagtype.ARBEIDSGIVERPERIODEDAG_NAV to SykepengeStatus.RETT_TIL_SYKEPENGER,
                    Utbetalingsdagtype.AVVIST_DAG to SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER,
                    Utbetalingsdagtype.FORELDET_DAG to SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER,
                    Utbetalingsdagtype.ARBEIDSGIVERPERIODE_DAG to null,
                    Utbetalingsdagtype.NAV_HELG_DAG to null,
                    Utbetalingsdagtype.FRIDAG to null,
                    Utbetalingsdagtype.FERIEDAG to null,
                    Utbetalingsdagtype.PERMISJONSDAG to null,
                    Utbetalingsdagtype.ARBEIDSDAG to null,
                    Utbetalingsdagtype.VENTETIDSDAG to null,
                    Utbetalingsdagtype.ARBEID_IKKE_GJENOPPTATT_DAG to null,
                    Utbetalingsdagtype.ANDRE_YTELSER to null,
                    Utbetalingsdagtype.UKJENT_DAG to null,
                ) { (type, forventetStatus) ->
                    daggrupper(dag(august(3), type)).map { it.sykepengeStatus } shouldBe listOfNotNull(forventetStatus)
                }
            }

            test("slår sammen påfølgende like dager til én daggruppe") {
                daggrupper(
                    dag(august(3), Utbetalingsdagtype.ARBEIDSGIVERPERIODEDAG_NAV),
                    dag(august(4), Utbetalingsdagtype.NAV_DAG),
                    dag(august(5), Utbetalingsdagtype.NAV_DAG),
                ) shouldBe listOf(daggruppe(antallDager = 3, beloepUtbetaltTilArbeidsgiver = 3000))
            }

            test("helg bryter ikke en daggruppe og telles ikke med i antall dager") {
                val toUkerMedHelg =
                    (3..14).map { august(it) }.map { dato ->
                        if (dato.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                            dag(dato, Utbetalingsdagtype.NAV_HELG_DAG, beloepTilArbeidsgiver = 0)
                        } else {
                            dag(dato)
                        }
                    }

                daggrupper(*toUkerMedHelg.toTypedArray()) shouldBe
                    listOf(daggruppe(antallDager = 10, beloepUtbetaltTilArbeidsgiver = 10000))
            }

            test("sorterer utbetalingsdagene før de slås sammen") {
                daggrupper(
                    dag(august(4), sykdomsgrad = 50),
                    dag(august(3)),
                    dag(august(5)),
                ) shouldBe
                    listOf(
                        daggruppe(antallDager = 1, beloepUtbetaltTilArbeidsgiver = 1000),
                        daggruppe(antallDager = 1, beloepUtbetaltTilArbeidsgiver = 1000, sykdomsgrad = 50),
                        daggruppe(antallDager = 1, beloepUtbetaltTilArbeidsgiver = 1000),
                    )
            }

            test("summerer beløp utbetalt til arbeidsgiver uten at det påvirker sammenslåingen") {
                daggrupper(
                    dag(august(3), beloepTilArbeidsgiver = 1000),
                    dag(august(4), beloepTilArbeidsgiver = 900),
                ) shouldBe listOf(daggruppe(antallDager = 2, beloepUtbetaltTilArbeidsgiver = 1900))
            }

            context("starter ny daggruppe når") {
                withData(
                    nameFn = { it.first },
                    "status endres" to dag(august(4), Utbetalingsdagtype.AVVIST_DAG),
                    "sykdomsgrad endres" to dag(august(4), sykdomsgrad = 50),
                    "refusjonskrav per dag endres" to dag(august(4), forespurtRefusjonBeloep = 500),
                ) { (_, endretDag) ->
                    daggrupper(dag(august(3)), endretDag) shouldHaveSize 2
                }
            }

            test("starter ny daggruppe når refusjonskrav per måned endres") {
                val refusjonskravMedEndring =
                    REFUSJONSKRAV.copy(endringer = listOf(RefusjonEndring(beloep = 15000.0, startdato = august(4))))

                daggrupper(dag(august(3)), dag(august(4)), refusjonskrav = refusjonskravMedEndring) shouldBe
                    listOf(
                        daggruppe(antallDager = 1, beloepUtbetaltTilArbeidsgiver = 1000),
                        daggruppe(antallDager = 1, beloepUtbetaltTilArbeidsgiver = 1000, beloepKrevdRefusjonPerMaaned = 15000.0),
                    )
            }

            test("grupperer dager uten rett til sykepenger etter samme regler") {
                daggrupper(
                    dag(august(3), Utbetalingsdagtype.AVVIST_DAG, sykdomsgrad = 10, beloepTilArbeidsgiver = 0),
                    dag(august(4), Utbetalingsdagtype.FORELDET_DAG, sykdomsgrad = 10, beloepTilArbeidsgiver = 0),
                    dag(august(5), Utbetalingsdagtype.AVVIST_DAG, sykdomsgrad = 15, beloepTilArbeidsgiver = 0),
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

            test("refusjonskrav per måned er null når datoen mangler i daglisten") {
                refusjonUtfall(dag(august(3)))
                    .copy(dager = emptyList())
                    .tilRefusjonUtfallPdfData()
                    .daggrupper
                    .single()
                    .beloepKrevdRefusjonPerMaaned shouldBe null
            }
        }
    })

private fun august(dag: Int): LocalDate = LocalDate.of(2026, 8, dag)

private fun dag(
    dato: LocalDate,
    type: Utbetalingsdagtype = Utbetalingsdagtype.NAV_DAG,
    sykdomsgrad: Int = 100,
    beloepTilArbeidsgiver: Int = 1000,
    forespurtRefusjonBeloep: Int = 1000,
): Utbetalingsdag =
    utbetalingsdagMock(
        dato = dato,
        type = type,
        sykdomsgrad = sykdomsgrad,
        beloepTilArbeidsgiver = beloepTilArbeidsgiver,
        forespurtRefusjonBeloep = forespurtRefusjonBeloep,
        hagInntektsmeldingReferanse = IM_ID,
    )

private fun daggruppe(
    antallDager: Int,
    beloepUtbetaltTilArbeidsgiver: Int,
    status: SykepengeStatus = SykepengeStatus.RETT_TIL_SYKEPENGER,
    sykdomsgrad: Int = 100,
    beloepKrevdRefusjonPerDag: Int = 1000,
    beloepKrevdRefusjonPerMaaned: Double? = REFUSJONSKRAV.beloepPerMaaned,
): Daggruppe =
    Daggruppe(
        antallDager = antallDager,
        sykepengeStatus = status,
        sykdomsgrad = sykdomsgrad,
        beloepKrevdRefusjonPerDag = beloepKrevdRefusjonPerDag,
        beloepKrevdRefusjonPerMaaned = beloepKrevdRefusjonPerMaaned,
        beloepUtbetaltTilArbeidsgiver = beloepUtbetaltTilArbeidsgiver,
    )

private fun refusjonUtfall(
    vararg utbetalingsdager: Utbetalingsdag,
    refusjonskrav: Refusjon = REFUSJONSKRAV,
): RefusjonUtfall =
    arbeidstakerVedtakMeldingMock(Fnr.genererGyldig(), Orgnr.genererGyldig())
        .copy(utbetalingsdager = utbetalingsdager.toList())
        .tilRefusjonUtfall(
            refusjonUtfallId = UUID.randomUUID().toKotlinUuid(),
            refusjonskrav = listOf(InntektsmeldingRefusjonskrav(IM_ID, refusjonskrav)),
            arbeidsgiverNavn = "Billys Bollefabrikk AS",
            sykmeldtNavn = "Ola Nordmann",
        )

private fun daggrupper(
    vararg utbetalingsdager: Utbetalingsdag,
    refusjonskrav: Refusjon = REFUSJONSKRAV,
): List<Daggruppe> =
    refusjonUtfall(*utbetalingsdager, refusjonskrav = refusjonskrav)
        .tilRefusjonUtfallPdfData()
        .daggrupper
