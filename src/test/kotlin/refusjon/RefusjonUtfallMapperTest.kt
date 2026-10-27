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

        fun map(
            utbetalingsdager: List<Utbetalingsdag>,
            refusjonskrav: List<InntektsmeldingRefusjonskrav>,
        ): RefusjonUtfall =
            arbeidstakerVedtakMeldingMock(fnr, orgnr)
                .copy(utbetalingsdager = utbetalingsdager)
                .tilRefusjonUtfall(refusjonUtfallId, refusjonskrav, "Arbeidsgiver AS", "Ola Nordmann")

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
                    fattetTidspunkt = melding.vedtakFattetTidspunkt,
                    sykmeldtNavn = "Ola Nordmann",
                    arbeidsgiverNavn = "Arbeidsgiver AS",
                    dager = emptyList(),
                    arbeidstakerVedtakMelding = melding,
                )
        }

        test("mapper utbetalingsdag til dag med refusjonskrav fra refererte inntektsmelding") {
            val dato = LocalDate.of(2026, 7, 28)
            val refusjonskrav = listOf(InntektsmeldingRefusjonskrav(imId, Refusjon(beloepPerMaaned = 30000.0, endringer = emptyList())))

            map(listOf(utbetalingsdag(dato)), refusjonskrav).dager shouldBe
                listOf(
                    Dag(
                        dato = dato,
                        dagtype = DagType.SYKEPENGER,
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

            map(dager.map { utbetalingsdag(it) }, refusjonskrav).dager.map { it.beloepKrevdRefusjonPerMaaned } shouldBe
                listOf(30000.0, 30000.0, 20000.0, 20000.0, 10000.0, 10000.0)
        }

        test("beholder desimaler i refusjonsbeløp per måned") {
            val refusjonskrav = listOf(InntektsmeldingRefusjonskrav(imId, Refusjon(beloepPerMaaned = 30000.5, endringer = emptyList())))

            map(listOf(utbetalingsdag(LocalDate.of(2026, 7, 28))), refusjonskrav).dager.single().beloepKrevdRefusjonPerMaaned shouldBe
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

                map(listOf(utbetalingsdag(LocalDate.of(2026, 7, 28), referanse)), refusjonskrav)
                    .dager
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
                Utbetalingsdagtype.ARBEIDSGIVERPERIODE_DAG to DagType.ARBEIDSGIVERPERIODE,
                Utbetalingsdagtype.ARBEIDSGIVERPERIODEDAG_NAV to DagType.SYKEPENGER,
                Utbetalingsdagtype.NAV_DAG to DagType.SYKEPENGER,
                Utbetalingsdagtype.NAV_HELG_DAG to DagType.HELG,
                Utbetalingsdagtype.FRIDAG to DagType.FRI,
                Utbetalingsdagtype.FERIEDAG to DagType.FERIE,
                Utbetalingsdagtype.PERMISJONSDAG to DagType.PERMISJON,
                Utbetalingsdagtype.ARBEIDSDAG to DagType.ARBEIDSDAG,
                Utbetalingsdagtype.AVVIST_DAG to DagType.IKKE_RETT_PAA_SYKEPENGER,
                Utbetalingsdagtype.FORELDET_DAG to DagType.IKKE_RETT_PAA_SYKEPENGER,
                Utbetalingsdagtype.VENTETIDSDAG to DagType.UKJENT,
                Utbetalingsdagtype.ARBEID_IKKE_GJENOPPTATT_DAG to DagType.UKJENT,
                Utbetalingsdagtype.ANDRE_YTELSER to DagType.UKJENT,
                Utbetalingsdagtype.UKJENT_DAG to DagType.UKJENT,
            ) { (utbetalingsdagtype, forventet) ->
                utbetalingsdagtype.tilDagType() shouldBe forventet
            }
        }
    })
