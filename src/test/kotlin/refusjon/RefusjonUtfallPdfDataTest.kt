package no.nav.helsearbeidsgiver.refusjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.util.UUID
import kotlin.uuid.toKotlinUuid

class RefusjonUtfallPdfDataTest :
    FunSpec({
        test("bruker verdier fra refusjonsutfallet uendret") {
            val refusjonUtfall =
                arbeidstakerVedtakMeldingMock(Fnr.genererGyldig(), Orgnr.genererGyldig())
                    .copy(
                        utbetalingsdager =
                            listOf(
                                utbetalingsdagMock(dato = LocalDate.of(2026, 8, 3)),
                                utbetalingsdagMock(dato = LocalDate.of(2026, 8, 4), sykdomsgrad = 50, beloepTilArbeidsgiver = 500),
                            ),
                    ).tilRefusjonUtfall(
                        refusjonUtfallId = UUID.randomUUID().toKotlinUuid(),
                        refusjonskrav = emptyList(),
                        arbeidsgiverNavn = "Billys Bollefabrikk AS",
                        sykmeldtNavn = "Ola Nordmann",
                    )

            val pdfData = refusjonUtfall.tilRefusjonUtfallPdfData()

            refusjonUtfall.daggrupper.shouldNotBeEmpty()
            pdfData.refusjonUtfallId shouldBe refusjonUtfall.refusjonUtfallId
            pdfData.daggrupper shouldBe refusjonUtfall.daggrupper
            pdfData.refusjonskrav shouldBe refusjonUtfall.refusjonskrav
            pdfData.beloepUtbetaltTilArbeidsgiver shouldBe refusjonUtfall.beloepUtbetaltTilArbeidsgiver
        }

        context("utbetalingen kan være lavere enn forventet når minst én daggruppe har differanse") {
            withData(
                nameFn = { (statuser, forventet) -> "$statuser gir $forventet" },
                emptyList<SykepengeStatus>() to false,
                listOf(SykepengeStatus.RETT_TIL_SYKEPENGER, SykepengeStatus.IKKE_RETT_TIL_SYKEPENGER) to false,
                listOf(SykepengeStatus.RETT_TIL_SYKEPENGER, SykepengeStatus.RETT_TIL_SYKEPENGER_DIFFERANSE) to true,
            ) { (statuser, forventet) ->
                val refusjonUtfall =
                    arbeidstakerVedtakMeldingMock(Fnr.genererGyldig(), Orgnr.genererGyldig())
                        .tilRefusjonUtfall(UUID.randomUUID().toKotlinUuid(), emptyList(), "Billys Bollefabrikk AS", "Ola Nordmann")
                        .copy(
                            daggrupper =
                                statuser.map {
                                    Daggruppe(
                                        antallDager = 1,
                                        sykepengeStatus = it,
                                        sykdomsgrad = 100,
                                        beloepUtbetaltTilArbeidsgiverPerDag = 0,
                                        beloepUtbetaltTilArbeidsgiver = 0,
                                    )
                                },
                        )

                refusjonUtfall.tilRefusjonUtfallPdfData().utbetalingKanVaereLavereEnnForventet shouldBe forventet
            }
        }
    })
