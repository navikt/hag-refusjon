package no.nav.helsearbeidsgiver.refusjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RefusjonEndring
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

        fun tilDager(
            utbetalingsdager: List<Utbetalingsdag>,
            refusjonskrav: List<InntektsmeldingRefusjonskrav>,
        ): List<Dag> =
            arbeidstakerVedtakMeldingMock(fnr, orgnr)
                .copy(utbetalingsdager = utbetalingsdager)
                .tilDager(refusjonskrav)

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
                    beloepKrevdAvArbeidsgiver = 0,
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

        test("grupperer dagene og summerer utbetalt beløp for alle dager") {
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

            refusjonUtfall.beloepKrevdAvArbeidsgiver shouldBe 2000
            refusjonUtfall.beloepUtbetaltTilArbeidsgiver shouldBe 2000
            refusjonUtfall.daggrupper shouldBe
                listOf(
                    Daggruppe(
                        antallDager = 2,
                        sykepengeStatus = SykepengeStatus.RETT_TIL_SYKEPENGER,
                        sykdomsgrad = 100,
                        beloepKrevdRefusjonPerDag = 1000,
                        beloepKrevdRefusjonPerMaaned = null,
                        beloepKrevdRefusjon = 2000,
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

        test("mapper utbetalingsdag til dag med refusjonskrav fra refererte inntektsmelding") {
            val dato = LocalDate.of(2026, 7, 28)
            val refusjonskrav = listOf(InntektsmeldingRefusjonskrav(imId, Refusjon(beloepPerMaaned = 30000.0, endringer = emptyList())))

            tilDager(listOf(utbetalingsdag(dato)), refusjonskrav) shouldBe
                listOf(
                    Dag(
                        dato = dato,
                        dagtype = Dagtype.NAV_DAG,
                        sykdomsgrad = 100,
                        beloepUtbetaltTilArbeidsgiver = 1000,
                        beloepKrevdRefusjonPerDag = 1000,
                        beloepKrevdRefusjonPerMaaned = 30000.0,
                    ),
                )
        }

        test("bruker gjeldende endring i refusjonskravet for hver dato") {
            val refusjonskrav =
                listOf(
                    InntektsmeldingRefusjonskrav(
                        imId,
                        Refusjon(
                            beloepPerMaaned = 30000.0,
                            endringer =
                                listOf(
                                    RefusjonEndring(beloep = 10000.0, startdato = LocalDate.of(2026, 8, 3)),
                                    RefusjonEndring(beloep = 20000.0, startdato = LocalDate.of(2026, 7, 30)),
                                ),
                        ),
                    ),
                )
            val dager =
                listOf(28, 29, 30, 31).map { LocalDate.of(2026, 7, it) } +
                    listOf(3, 4).map { LocalDate.of(2026, 8, it) }

            tilDager(dager.map { utbetalingsdag(it) }, refusjonskrav).map { it.beloepKrevdRefusjonPerMaaned } shouldBe
                listOf(30000.0, 30000.0, 20000.0, 20000.0, 10000.0, 10000.0)
        }

        test("beholder desimaler i refusjonsbeløp per måned") {
            val refusjonskrav = listOf(InntektsmeldingRefusjonskrav(imId, Refusjon(beloepPerMaaned = 30000.5, endringer = emptyList())))

            tilDager(listOf(utbetalingsdag(LocalDate.of(2026, 7, 28))), refusjonskrav).single().beloepKrevdRefusjonPerMaaned shouldBe
                30000.5
        }

        context("beloepPaaDato") {
            val refusjonUtenEndringer = Refusjon(beloepPerMaaned = 30000.0, endringer = emptyList())
            val refusjonMedEndringer =
                Refusjon(
                    beloepPerMaaned = 30000.0,
                    endringer =
                        listOf(
                            RefusjonEndring(beloep = 0.0, startdato = LocalDate.of(2026, 8, 10)),
                            RefusjonEndring(beloep = 20000.0, startdato = LocalDate.of(2026, 8, 1)),
                            RefusjonEndring(beloep = 25000.0, startdato = LocalDate.of(2026, 8, 5)),
                        ),
                )

            test("bruker beløp per måned når det ikke finnes endringer") {
                refusjonUtenEndringer.beloepPaaDato(LocalDate.of(2026, 8, 1)) shouldBe 30000.0
            }

            withData(
                nameFn = { (dato, forventet) -> "$dato gir $forventet" },
                LocalDate.of(2026, 7, 31) to 30000.0,
                LocalDate.of(2026, 8, 1) to 20000.0,
                LocalDate.of(2026, 8, 4) to 20000.0,
                LocalDate.of(2026, 8, 5) to 25000.0,
                LocalDate.of(2026, 8, 9) to 25000.0,
                LocalDate.of(2026, 8, 10) to 0.0,
                LocalDate.of(2026, 12, 31) to 0.0,
            ) { (dato, forventet) ->
                refusjonMedEndringer.beloepPaaDato(dato) shouldBe forventet
            }
        }

        context("refusjonsbeløp per måned er null når kravet mangler") {
            withData(
                nameFn = { it.first },
                "dagen mangler referanse til inntektsmelding" to
                    (null to listOf(InntektsmeldingRefusjonskrav(imId, Refusjon(1.0, emptyList())))),
                "inntektsmeldingen finnes ikke blant refusjonskravene" to (imId to emptyList()),
                "inntektsmeldingen har ikke refusjonskrav" to (imId to listOf(InntektsmeldingRefusjonskrav(imId, null))),
            ) { (_, testdata) ->
                val (referanse, refusjonskrav) = testdata

                tilDager(listOf(utbetalingsdag(LocalDate.of(2026, 7, 28), referanse)), refusjonskrav)
                    .single()
                    .beloepKrevdRefusjonPerMaaned shouldBe null
            }
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

        context("mapper utbetalingsdagtype til dagtype") {
            withData(
                Utbetalingsdagtype.NAV_DAG to Dagtype.NAV_DAG,
                Utbetalingsdagtype.NAV_HELG_DAG to Dagtype.HELG,
                Utbetalingsdagtype.FRIDAG to Dagtype.FRI,
                Utbetalingsdagtype.FERIEDAG to Dagtype.FERIE,
                Utbetalingsdagtype.PERMISJONSDAG to Dagtype.PERMISJON,
                Utbetalingsdagtype.ARBEIDSDAG to Dagtype.ARBEIDSDAG,
                Utbetalingsdagtype.AVVIST_DAG to Dagtype.IKKE_RETT_PAA_SYKEPENGER,
                Utbetalingsdagtype.FORELDET_DAG to Dagtype.IKKE_RETT_PAA_SYKEPENGER,
                Utbetalingsdagtype.VENTETIDSDAG to Dagtype.UKJENT,
                Utbetalingsdagtype.ARBEID_IKKE_GJENOPPTATT_DAG to Dagtype.UKJENT,
                Utbetalingsdagtype.ANDRE_YTELSER to Dagtype.UKJENT,
                Utbetalingsdagtype.UKJENT_DAG to Dagtype.UKJENT,
            ) { (utbetalingsdagtype, forventet) ->
                utbetalingsdagMock(type = utbetalingsdagtype).tilDagType() shouldBe forventet
            }
        }

        context("arbeidsgiverperiodedager er Nav-dager bare når Nav utbetaler til arbeidsgiver") {
            withData(
                nameFn = { (type, beloep, forventet) -> "$type med $beloep kr til arbeidsgiver gir $forventet" },
                Triple(Utbetalingsdagtype.ARBEIDSGIVERPERIODE_DAG, 1000, Dagtype.NAV_DAG),
                Triple(Utbetalingsdagtype.ARBEIDSGIVERPERIODE_DAG, 0, Dagtype.ARBEIDSGIVERPERIODE),
                Triple(Utbetalingsdagtype.ARBEIDSGIVERPERIODEDAG_NAV, 1000, Dagtype.NAV_DAG),
                Triple(Utbetalingsdagtype.ARBEIDSGIVERPERIODEDAG_NAV, 0, Dagtype.ARBEIDSGIVERPERIODE),
            ) { (type, beloep, forventet) ->
                utbetalingsdagMock(type = type, beloepTilArbeidsgiver = beloep).tilDagType() shouldBe forventet
            }
        }
    })
