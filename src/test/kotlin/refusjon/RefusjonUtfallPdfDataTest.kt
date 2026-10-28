package no.nav.helsearbeidsgiver.refusjon

import io.kotest.core.spec.style.FunSpec
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
            pdfData.beloepKrevdAvArbeidsgiver shouldBe refusjonUtfall.beloepKrevdAvArbeidsgiver
            pdfData.beloepUtbetaltTilArbeidsgiver shouldBe refusjonUtfall.beloepUtbetaltTilArbeidsgiver
            pdfData.dagerIgjenMedRettTilSykepenger shouldBe refusjonUtfall.dagerIgjenMedRettTilSykepenger
        }
    })
