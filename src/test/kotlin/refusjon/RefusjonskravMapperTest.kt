package no.nav.helsearbeidsgiver.refusjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RefusjonEndring
import java.time.LocalDate
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class RefusjonskravMapperTest :
    FunSpec({
        val imId = UUID.randomUUID().toKotlinUuid()
        val annenImId = UUID.randomUUID().toKotlinUuid()
        val imIdUtenKrav = UUID.randomUUID().toKotlinUuid()

        fun dag(
            dato: LocalDate,
            imReferanse: Uuid? = imId,
            type: Utbetalingsdagtype = Utbetalingsdagtype.NAV_DAG,
        ): Utbetalingsdag = utbetalingsdagMock(dato = dato, type = type, hagInntektsmeldingReferanse = imReferanse)

        fun refusjon(
            beloepPerMaaned: Double,
            vararg endringer: Pair<LocalDate, Double>,
        ): Refusjon =
            Refusjon(
                beloepPerMaaned,
                endringer.map { (startdato, beloep) ->
                    RefusjonEndring(beloep = beloep, startdato = startdato)
                },
            )

        fun krav(
            beloepPerMaaned: Double,
            vararg endringer: Pair<LocalDate, Double>,
        ): Refusjonskrav = Refusjonskrav(beloepPerMaaned, endringer.map { (startdato, beloep) -> RefusjonskravEndring(startdato, beloep) })

        context("tilRefusjonskrav") {
            test("ingen dager gir ikke refusjonskrav") {
                emptyList<Utbetalingsdag>().tilRefusjonskrav(mapOf(imId to refusjon(30000.0))) shouldBe null
            }

            context("ingen dager med refusjonskrav gir ikke refusjonskrav") {
                withData(
                    nameFn = { it.first },
                    "dagene mangler referanse til inntektsmelding" to null,
                    "inntektsmeldingen finnes ikke blant refusjonskravene" to UUID.randomUUID().toKotlinUuid(),
                    "inntektsmeldingen har ikke refusjonskrav" to imIdUtenKrav,
                ) { (_, imReferanse) ->
                    listOf(dag(august(3), imReferanse), dag(august(4), imReferanse))
                        .tilRefusjonskrav(mapOf(imId to refusjon(30000.0), imIdUtenKrav to null)) shouldBe null
                }
            }

            test("uendret refusjonskrav gir ingen endringer") {
                listOf(dag(august(3)), dag(august(4)), dag(august(5)))
                    .tilRefusjonskrav(mapOf(imId to refusjon(30000.5))) shouldBe krav(30000.5)
            }

            test("endringer i perioden blir endringer med startdato fra inntektsmeldingen") {
                val refusjonskrav = mapOf(imId to refusjon(30000.0, august(5) to 20000.0, august(7) to 0.0))

                (3..10).map { dag(august(it)) }.tilRefusjonskrav(refusjonskrav) shouldBe
                    krav(30000.0, august(5) to 20000.0, august(7) to 0.0)
            }

            test("endring som starter på en helgedag beholder startdatoen") {
                val refusjonskrav = mapOf(imId to refusjon(30000.0, august(8) to 20000.0))
                val dager =
                    listOf(
                        dag(august(7)),
                        dag(august(8), type = Utbetalingsdagtype.NAV_HELG_DAG),
                        dag(august(9), type = Utbetalingsdagtype.NAV_HELG_DAG),
                        dag(august(10)),
                    )

                dager.tilRefusjonskrav(refusjonskrav) shouldBe krav(30000.0, august(8) to 20000.0)
            }

            test("endringer før første dag inngår i beløp per måned, og endringer etter siste dag tas ikke med") {
                val refusjonskrav =
                    mapOf(imId to refusjon(30000.0, juli(1) to 25000.0, august(3) to 20000.0, august(31) to 0.0))

                listOf(dag(august(4)), dag(august(5))).tilRefusjonskrav(refusjonskrav) shouldBe krav(20000.0)
            }

            test("endring som starter på første dag blir beløp per måned og ikke en endring") {
                val refusjonskrav = mapOf(imId to refusjon(30000.0, august(3) to 20000.0))

                listOf(dag(august(3)), dag(august(4))).tilRefusjonskrav(refusjonskrav) shouldBe krav(20000.0)
            }

            test("endring tilbake til tidligere beløp blir egen endring") {
                val refusjonskrav = mapOf(imId to refusjon(30000.0, august(4) to 20000.0, august(5) to 30000.0))

                (3..5).map { dag(august(it)) }.tilRefusjonskrav(refusjonskrav) shouldBe
                    krav(30000.0, august(4) to 20000.0, august(5) to 30000.0)
            }

            test("sorterer dagene etter dato") {
                val refusjonskrav = mapOf(imId to refusjon(30000.0, august(4) to 20000.0))

                listOf(dag(august(5)), dag(august(3)), dag(august(4))).tilRefusjonskrav(refusjonskrav) shouldBe
                    krav(30000.0, august(4) to 20000.0)
            }

            test("beløp per måned kommer fra første dag med refusjonskrav") {
                listOf(dag(august(3), null), dag(august(4), imIdUtenKrav), dag(august(5)), dag(august(6)))
                    .tilRefusjonskrav(mapOf(imId to refusjon(30000.0), imIdUtenKrav to null)) shouldBe krav(30000.0)
            }

            test("dager uten refusjonskrav midt i perioden hoppes over") {
                listOf(dag(august(3)), dag(august(4), null), dag(august(5), imIdUtenKrav), dag(august(6)))
                    .tilRefusjonskrav(mapOf(imId to refusjon(30000.0), imIdUtenKrav to null)) shouldBe krav(30000.0)
            }

            test("nytt beløp etter dager uten refusjonskrav blir endring fra første dag med krav etter hullet") {
                val dager = listOf(dag(august(3)), dag(august(4), null), dag(august(5), annenImId))

                dager.tilRefusjonskrav(mapOf(imId to refusjon(30000.0), annenImId to refusjon(20000.0))) shouldBe
                    krav(30000.0, august(5) to 20000.0)
            }

            test("endring som starter på en dag uten refusjonskrav får startdato fra neste dag med krav") {
                val refusjonskrav = mapOf(imId to refusjon(30000.0, august(4) to 20000.0))

                listOf(dag(august(3)), dag(august(4), null), dag(august(5))).tilRefusjonskrav(refusjonskrav) shouldBe
                    krav(30000.0, august(5) to 20000.0)
            }

            test("endring som starter på en dato som ikke er i daglista får startdato fra neste dag i daglista") {
                val refusjonskrav = mapOf(imId to refusjon(30000.0, august(4) to 20000.0))

                listOf(dag(august(3)), dag(august(5))).tilRefusjonskrav(refusjonskrav) shouldBe
                    krav(30000.0, august(5) to 20000.0)
            }

            test("bruker refusjonskravet fra inntektsmeldingen hver dag peker på") {
                val refusjonskrav =
                    mapOf(
                        imId to refusjon(30000.0, august(4) to 25000.0),
                        annenImId to refusjon(20000.0, august(7) to 10000.0),
                    )
                val dager =
                    listOf(
                        dag(august(3)),
                        dag(august(4)),
                        dag(august(5), annenImId),
                        dag(august(6), annenImId),
                        dag(august(7), annenImId),
                    )

                dager.tilRefusjonskrav(refusjonskrav) shouldBe
                    krav(30000.0, august(4) to 25000.0, august(5) to 20000.0, august(7) to 10000.0)
            }

            test("bytte av inntektsmelding med samme beløp gir ingen endring") {
                listOf(dag(august(3)), dag(august(4), annenImId))
                    .tilRefusjonskrav(mapOf(imId to refusjon(30000.0), annenImId to refusjon(30000.0))) shouldBe krav(30000.0)
            }
        }

        context("refusjonskravPerMaaned") {
            test("bruker refusjonskravet fra inntektsmeldingen dagen peker på") {
                dag(august(3)).refusjonskravPerMaaned(mapOf(imId to refusjon(30000.5))) shouldBe 30000.5
            }

            context("er null når kravet mangler") {
                withData(
                    nameFn = { it.first },
                    "dagen mangler referanse til inntektsmelding" to null,
                    "inntektsmeldingen finnes ikke blant refusjonskravene" to UUID.randomUUID().toKotlinUuid(),
                    "inntektsmeldingen har ikke refusjonskrav" to imIdUtenKrav,
                ) { (_, imReferanse) ->
                    dag(august(3), imReferanse)
                        .refusjonskravPerMaaned(mapOf(imId to refusjon(30000.0), imIdUtenKrav to null)) shouldBe null
                }
            }
        }

        context("beloepPaaDato") {
            val refusjonMedEndringer =
                refusjon(
                    30000.0,
                    august(10) to 0.0,
                    august(1) to 20000.0,
                    august(5) to 25000.0,
                )

            test("bruker beløp per måned når det ikke finnes endringer") {
                refusjon(30000.0).beloepPaaDato(august(1)) shouldBe 30000.0
            }

            withData(
                nameFn = { (dato, forventet) -> "$dato gir $forventet" },
                juli(31) to 30000.0,
                august(1) to 20000.0,
                august(4) to 20000.0,
                august(5) to 25000.0,
                august(9) to 25000.0,
                august(10) to 0.0,
                LocalDate.of(2026, 12, 31) to 0.0,
            ) { (dato, forventet) ->
                refusjonMedEndringer.beloepPaaDato(dato) shouldBe forventet
            }
        }
    })

private fun juli(dag: Int): LocalDate = LocalDate.of(2026, 7, dag)

private fun august(dag: Int): LocalDate = LocalDate.of(2026, 8, dag)
