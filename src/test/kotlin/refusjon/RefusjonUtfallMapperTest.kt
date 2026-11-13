package no.nav.helsearbeidsgiver.refusjon

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.refusjonskrav.InntektsmeldingRefusjonskrav
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
                    sykmeldingId = melding.dokumenter.first { it.type.equals(Dokument.Type.Sykmelding) }.dokumentId,
                    inntektsmeldingIder = emptyList(),
                    fnr = fnr,
                    orgnr = orgnr,
                    fom = melding.fom,
                    tom = melding.tom,
                    sykepengegrunnlag = melding.sykepengegrunnlag,
                    arbeidstakervedtakFattetTidspunkt = melding.vedtakFattetTidspunkt,
                    sykmeldtNavn = "Ola Nordmann",
                    arbeidsgiverNavn = "Arbeidsgiver AS",
                    daggrupper = emptyList(),
                    refusjonskrav = null,
                    beloepUtbetaltTilArbeidsgiver = 0,
                    arbeidstakerVedtakMelding = melding,
                )
        }

        test("grupperer dagene og summerer beløp utbetalt til arbeidsgiver fra daggruppene") {
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

            refusjonUtfall.beloepUtbetaltTilArbeidsgiver shouldBe 2000
            refusjonUtfall.daggrupper shouldBe
                listOf(
                    Daggruppe(
                        antallDager = 2,
                        sykepengeStatus = SykepengeStatus.RETT_TIL_SYKEPENGER,
                        sykdomsgrad = 100,
                        beloepUtbetaltTilArbeidsgiverPerDag = 1000,
                        beloepUtbetaltTilArbeidsgiver = 2000,
                    ),
                )
        }

        test("bygger refusjonskravet fra inntektsmeldingene dagene peker på") {
            val annenImId = UUID.randomUUID().toKotlinUuid()
            val melding =
                arbeidstakerVedtakMeldingMock(fnr, orgnr).copy(
                    utbetalingsdager =
                        listOf(
                            utbetalingsdag(LocalDate.of(2026, 7, 30), null),
                            utbetalingsdag(LocalDate.of(2026, 7, 31), imId),
                            utbetalingsdag(LocalDate.of(2026, 8, 3), annenImId),
                        ),
                )
            val refusjonskrav =
                listOf(
                    InntektsmeldingRefusjonskrav(imId, Refusjon(beloepPerMaaned = 26000.0, endringer = emptyList())),
                    InntektsmeldingRefusjonskrav(annenImId, Refusjon(beloepPerMaaned = 13000.0, endringer = emptyList())),
                )

            val refusjonUtfall = melding.tilRefusjonUtfall(refusjonUtfallId, refusjonskrav, "Arbeidsgiver AS", "Ola Nordmann")

            refusjonUtfall.refusjonskrav shouldBe
                Refusjonskrav(
                    beloepPerMaaned = 26000.0,
                    endringer = listOf(RefusjonskravEndring(startdato = LocalDate.of(2026, 8, 3), beloep = 13000.0)),
                )
        }

        test("bruker anvendt 6G fra vedtaket") {
            // 26 000 kr per måned × 12 = 312 000 kr per år, som begrenses til en anvendt 6G på 260 000 kr (1 000 kr per dag)
            val melding =
                arbeidstakerVedtakMeldingMock(fnr, orgnr).copy(
                    anvendtSeksG = 260000.0,
                    utbetalingsdager = listOf(utbetalingsdag(LocalDate.of(2026, 7, 28))),
                )
            val refusjonskrav = listOf(InntektsmeldingRefusjonskrav(imId, Refusjon(beloepPerMaaned = 26000.0, endringer = emptyList())))

            melding
                .tilRefusjonUtfall(refusjonUtfallId, refusjonskrav, "Arbeidsgiver AS", "Ola Nordmann")
                .daggrupper
                .single()
                .sykepengeStatus shouldBe SykepengeStatus.RETT_TIL_SYKEPENGER
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

        test("manglende sykmeldingId kaster exception") {
            val melding =
                arbeidstakerVedtakMeldingMock(fnr, orgnr).copy(
                    dokumenter = emptyList(),
                )
            shouldThrow<NoSuchElementException> {
                melding.tilRefusjonUtfall(Uuid.random(), emptyList(), "Arbeidsgiver AS", "Ola Nordmann")
            }
        }
    })
