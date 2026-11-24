package no.nav.helsearbeidsgiver.refusjon

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpStatusCode
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import no.nav.helsearbeidsgiver.arkiv.ArkivService
import no.nav.helsearbeidsgiver.bucket.FakeBucketStorage
import no.nav.helsearbeidsgiver.kafka.RefusjonProducer
import no.nav.helsearbeidsgiver.kafka.TEST_TOPIC
import no.nav.helsearbeidsgiver.kafka.mockProducer
import no.nav.helsearbeidsgiver.person.PdlService
import no.nav.helsearbeidsgiver.refusjonskrav.RefusjonskravService
import no.nav.helsearbeidsgiver.utils.UnleashFeatureToggles
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.virksomhet.VirksomhetsnavnKlient
import kotlin.uuid.Uuid

class VedtakServiceTest :
    FunSpec({
        val pdfBytes = "PDF innhold".toByteArray()
        val melding = gyldigMelding(FNR, ORGNR).parseJson().fromJson(ArbeidstakerVedtakMelding.serializer())

        val refusjonskravService =
            mockk<RefusjonskravService> {
                coEvery { hentRefusjonskrav(any()) } returns emptyList()
            }

        afterEach {
            unmockkAll()
            clearMocks(refusjonskravService, answers = false)
        }

        fun vedtakService(
            bucketStorage: FakeBucketStorage = FakeBucketStorage(),
            refusjonProducer: RefusjonProducer = RefusjonProducer(mockProducer(), TEST_TOPIC),
            virksomhetsnavnKlient: VirksomhetsnavnKlient = virksomhetsnavnKlientMock(),
            pdlService: PdlService = pdlServiceMock(),
            arkivService: ArkivService = arkivServiceMock(),
            repository: RefusjonUtfallRepository = utfallRepositoryMock(),
            unleashFeatureToggles: UnleashFeatureToggles = unleashFeatureTogglesMock(),
        ) = VedtakService(
            bucketStorage,
            refusjonProducer,
            virksomhetsnavnKlient,
            pdlService,
            arkivService,
            refusjonskravService,
            repository,
            unleashFeatureToggles,
        )

        listOf(
            "uten utbetaling til arbeidsgiver" to 0,
            "selv med utbetaling til arbeidsgiver" to 1431,
        ).forEach { (beskrivelse, beloepTilArbeidsgiver) ->
            test("gjør ingenting når arbeidsgiver ikke har ønsket refusjon, $beskrivelse") {
                val bucketStorage = FakeBucketStorage()
                val mockProducer = mockProducer()
                val arkivService = arkivServiceMock()
                val repository = utfallRepositoryMock()
                val klient = virksomhetsnavnKlientMock()
                val pdl = pdlServiceMock()
                mockPdfgen(HttpStatusCode.OK, pdfBytes)
                val ikkeOensketRefusjon =
                    melding.copy(
                        harArbeidsgiverOensketRefusjon = false,
                        utbetalingsdager = melding.utbetalingsdager.map { it.copy(beloepTilArbeidsgiver = beloepTilArbeidsgiver) },
                    )

                vedtakService(
                    bucketStorage,
                    RefusjonProducer(mockProducer, TEST_TOPIC),
                    virksomhetsnavnKlient = klient,
                    pdlService = pdl,
                    arkivService = arkivService,
                    repository = repository,
                ).behandle(ikkeOensketRefusjon)

                repository.hent(melding.behandlingId).shouldBeNull()
                verify(exactly = 0) { repository.lagre(any(), any()) }
                bucketStorage.pdfer.size shouldBe 0
                coVerify(exactly = 0) { arkivService.arkiver(any(), any()) }
                mockProducer.history().shouldBeEmpty()
                coVerify(exactly = 0) { refusjonskravService.hentRefusjonskrav(any()) }
                coVerify(exactly = 0) { klient.hentVirksomhetsnavn(ORGNR) }
                coVerify(exactly = 0) { pdl.hentSykmeldtnavn(FNR) }
            }
        }

        listOf(true, false).forEach { harArbeidsgiverOensketRefusjon ->
            test("feiler uten sideeffekter når vedtaket er ugyldig (ønsket refusjon: $harArbeidsgiverOensketRefusjon)") {
                val bucketStorage = FakeBucketStorage()
                val mockProducer = mockProducer()
                val arkivService = arkivServiceMock()
                val repository = utfallRepositoryMock()
                val klient = virksomhetsnavnKlientMock()
                val pdl = pdlServiceMock()
                mockPdfgen(HttpStatusCode.OK, pdfBytes)
                val ugyldigVedtak =
                    melding.copy(
                        dokumenter = emptyList(),
                        harArbeidsgiverOensketRefusjon = harArbeidsgiverOensketRefusjon,
                    )

                val feil =
                    shouldThrow<IllegalStateException> {
                        vedtakService(
                            bucketStorage,
                            RefusjonProducer(mockProducer, TEST_TOPIC),
                            virksomhetsnavnKlient = klient,
                            pdlService = pdl,
                            arkivService = arkivService,
                            repository = repository,
                        ).behandle(ugyldigVedtak)
                    }

                feil.cause.shouldBeInstanceOf<UgyldigArbeidstakerVedtakException>().feil shouldBe
                    listOf(ArbeidstakerVedtakValideringFeil.MANGLER_SYKMELDING)
                verify(exactly = 0) { repository.hent(any()) }
                verify(exactly = 0) { repository.lagre(any(), any()) }
                bucketStorage.pdfer.size shouldBe 0
                coVerify(exactly = 0) { arkivService.arkiver(any(), any()) }
                mockProducer.history().shouldBeEmpty()
                coVerify(exactly = 0) { refusjonskravService.hentRefusjonskrav(any()) }
                coVerify(exactly = 0) { klient.hentVirksomhetsnavn(ORGNR) }
                coVerify(exactly = 0) { pdl.hentSykmeldtnavn(FNR) }
            }
        }

        test("lagrer utfallet, men hopper over PDF, arkivering og publisering når feature toggle er av") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            val arkivService = arkivServiceMock()
            val repository = utfallRepositoryMock()
            val toggles = unleashFeatureTogglesMock(erJournalfoeringOgDistribusjonSkruddPaa = false)
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            vedtakService(
                bucketStorage,
                RefusjonProducer(mockProducer, TEST_TOPIC),
                arkivService = arkivService,
                repository = repository,
                unleashFeatureToggles = toggles,
            ).behandle(melding)

            repository.hent(melding.behandlingId).shouldNotBeNull()
            bucketStorage.pdfer.size shouldBe 0
            coVerify(exactly = 0) { arkivService.arkiver(any(), any()) }
            mockProducer.history().shouldBeEmpty()
            verify(exactly = 1) { toggles.skalJournalfoereOgDistribuereRefusjon(ORGNR) }
        }

        test("journalfører og distribuerer lagret utfall når jobben kjøres på nytt etter at feature toggle er slått på") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            val arkivService = arkivServiceMock()
            val repository = utfallRepositoryMock()
            val klient = virksomhetsnavnKlientMock()
            val pdl = pdlServiceMock()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            vedtakService(
                bucketStorage,
                RefusjonProducer(mockProducer, TEST_TOPIC),
                virksomhetsnavnKlient = klient,
                pdlService = pdl,
                arkivService = arkivService,
                repository = repository,
                unleashFeatureToggles = unleashFeatureTogglesMock(erJournalfoeringOgDistribusjonSkruddPaa = false),
            ).behandle(melding)

            bucketStorage.pdfer.size shouldBe 0
            mockProducer.history().shouldBeEmpty()
            val foersteId = checkNotNull(repository.hent(melding.behandlingId)).refusjonUtfallId

            vedtakService(
                bucketStorage,
                RefusjonProducer(mockProducer, TEST_TOPIC),
                virksomhetsnavnKlient = klient,
                pdlService = pdl,
                arkivService = arkivService,
                repository = repository,
                unleashFeatureToggles = unleashFeatureTogglesMock(erJournalfoeringOgDistribusjonSkruddPaa = true),
            ).behandle(melding)

            val andreId = checkNotNull(repository.hent(melding.behandlingId)).refusjonUtfallId
            andreId shouldBe foersteId
            bucketStorage.pdfer[andreId] shouldBe pdfBytes
            coVerify(exactly = 1) { arkivService.arkiver(match { it.refusjonUtfallId == andreId }, pdfBytes) }
            mockProducer.history() shouldHaveSize 1
            coVerify(exactly = 1) { klient.hentVirksomhetsnavn(ORGNR) }
            coVerify(exactly = 1) { pdl.hentSykmeldtnavn(FNR) }
        }

        test("lagrer utfallet og gjenbruker det uten nye oppslag når behandle kalles igjen") {
            val bucketStorage = FakeBucketStorage()
            val repository = utfallRepositoryMock()
            val klient = virksomhetsnavnKlientMock()
            val pdl = pdlServiceMock()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            vedtakService(
                bucketStorage,
                virksomhetsnavnKlient = klient,
                pdlService = pdl,
                repository = repository,
            ).behandle(melding)
            val foersteId = checkNotNull(repository.hent(melding.behandlingId)).refusjonUtfallId

            vedtakService(
                bucketStorage,
                virksomhetsnavnKlient = klient,
                pdlService = pdl,
                repository = repository,
            ).behandle(melding)

            repository.hent(melding.behandlingId)?.refusjonUtfallId shouldBe foersteId
            verify(exactly = 1) { repository.lagre(any(), any()) }
            coVerify(exactly = 1) { klient.hentVirksomhetsnavn(ORGNR) }
            coVerify(exactly = 1) { pdl.hentSykmeldtnavn(FNR) }
        }

        test("gjenbruker lagret PDF fra bucket i stedet for å generere den på nytt") {
            val bucketStorage = FakeBucketStorage()
            val repository = utfallRepositoryMock()
            val arkivService = arkivServiceMock()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            vedtakService(bucketStorage, arkivService = arkivService, repository = repository).behandle(melding)
            val refusjonsutfallId = checkNotNull(repository.hent(melding.behandlingId)).refusjonUtfallId

            // Hvis PDF-en ble generert på nytt ville den fått dette innholdet.
            mockPdfgen(HttpStatusCode.OK, "Annet innhold".toByteArray())
            vedtakService(bucketStorage, arkivService = arkivService, repository = repository).behandle(melding)

            bucketStorage.pdfer[refusjonsutfallId] shouldBe pdfBytes
            coVerify(exactly = 1) { arkivService.arkiver(any(), pdfBytes) }
            repository.hentJournalpostId(refusjonsutfallId) shouldBe JOURNALPOST_ID
        }

        test("bruker utfallet som allerede er lagret hvis et annet kall rakk å lagre først") {
            // skal vanligvis ikke skje, men teoretisk mulig at to podder kjører samtidig under deploy
            val bucketStorage = FakeBucketStorage()
            val eksisterende = melding.tilRefusjonUtfall(Uuid.random(), emptyList(), ARBEIDSGIVER_NAVN, SYKMELDT_NAVN)
            val repository =
                mockk<RefusjonUtfallRepository> {
                    every { hent(any()) } returnsMany listOf(null, eksisterende)
                    every { lagre(any(), any()) } returns false
                    every { hentJournalpostId(any()) } returns null
                    every { lagreJournalpostId(any(), any()) } returns Unit
                }
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            vedtakService(bucketStorage, repository = repository).behandle(melding)

            bucketStorage.pdfer.keys shouldBe setOf(eksisterende.refusjonUtfallId)
        }

        test("feiler når lagring av utfall feiler") {
            val bucketStorage = FakeBucketStorage()
            val repository =
                mockk<RefusjonUtfallRepository> {
                    every { hent(any()) } returns null
                    every { lagre(any(), any()) } throws RuntimeException("db nede")
                }
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            shouldThrow<IllegalStateException> {
                vedtakService(bucketStorage, repository = repository).behandle(melding)
            }

            bucketStorage.pdfer.size shouldBe 0
        }

        test("genererer PDF, lagrer den i bucket, arkiverer og publiserer refusjonsutfall") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            val arkivService = arkivServiceMock()
            val repository = utfallRepositoryMock()
            val producer = spyk(RefusjonProducer(mockProducer, TEST_TOPIC))
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            vedtakService(bucketStorage, producer, arkivService = arkivService, repository = repository)
                .behandle(melding)

            val refusjonsutfallId = checkNotNull(repository.hent(melding.behandlingId)).refusjonUtfallId
            bucketStorage.pdfer[refusjonsutfallId] shouldBe pdfBytes
            repository.hentJournalpostId(refusjonsutfallId) shouldBe JOURNALPOST_ID
            coVerifyOrder {
                arkivService.arkiver(any(), pdfBytes)
                repository.lagreJournalpostId(refusjonsutfallId, JOURNALPOST_ID)
                producer.send(melding.vedtaksperiodeId, any())
            }

            coVerify(exactly = 1) {
                arkivService.arkiver(
                    match { it.refusjonUtfallId == refusjonsutfallId && it.arbeidsgiverNavn == ARBEIDSGIVER_NAVN },
                    pdfBytes,
                )
            }
            coVerify(exactly = 1) {
                refusjonskravService.hentRefusjonskrav(listOf(Uuid.parse("65f84fa6-554e-4200-9466-30fc7e1ffb2e")))
            }

            val sendt = mockProducer.history()
            sendt shouldHaveSize 1
            sendt.first().key() shouldBe "c62594af-f0b8-4fd1-88f2-07e1b15dd906"
            val utfall =
                sendt
                    .first()
                    .value()
                    .parseJson()
                    .fromJson(RefusjonUtfall.serializer())
            utfall.refusjonUtfallId shouldBe refusjonsutfallId
            utfall.arbeidsgiverNavn shouldBe ARBEIDSGIVER_NAVN
            utfall.sykmeldtNavn shouldBe SYKMELDT_NAVN
            utfall.arbeidstakerVedtakMelding shouldBe melding
        }

        test("oppretter og publiserer refusjonsutfall uten inntektsmeldingreferanser") {
            val meldingUtenInntektsmelding =
                melding.copy(
                    utbetalingsdager =
                        melding.utbetalingsdager.map {
                            it.copy(forespurtRefusjon = it.forespurtRefusjon.copy(hagInntektsmeldingReferanse = null))
                        },
                )
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            val arkivService = arkivServiceMock()
            val repository = utfallRepositoryMock()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            vedtakService(
                bucketStorage,
                RefusjonProducer(mockProducer, TEST_TOPIC),
                arkivService = arkivService,
                repository = repository,
            ).behandle(meldingUtenInntektsmelding)

            val utfall =
                mockProducer
                    .history()
                    .single()
                    .value()
                    .parseJson()
                    .fromJson(RefusjonUtfall.serializer())
            utfall.inntektsmeldingIder.shouldBeEmpty()
            utfall.refusjonskrav.shouldBeNull()
            utfall.sykmeldingId shouldBe meldingUtenInntektsmelding.dokumenter.first { it.type == Dokument.Type.Sykmelding }.dokumentId
            bucketStorage.pdfer[utfall.refusjonUtfallId] shouldBe pdfBytes
            repository.hent(melding.behandlingId) shouldBe utfall
            coVerify(exactly = 1) { arkivService.arkiver(any(), pdfBytes) }
        }

        test("feiler og publiserer ingenting når publisering til Kafka feiler") {
            val mockProducer = mockProducer(autoComplete = false).apply { sendException = RuntimeException("kafka nede") }
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            shouldThrow<IllegalStateException> {
                vedtakService(refusjonProducer = RefusjonProducer(mockProducer, TEST_TOPIC)).behandle(melding)
            }

            mockProducer.history().shouldBeEmpty()
        }

        test("gjenbruker lagret journalpostId ved nytt forsøk etter feil ved publisering") {
            val bucketStorage = FakeBucketStorage()
            val repository = utfallRepositoryMock()
            val arkivService = arkivServiceMock()
            val feilendeProducer = mockProducer(autoComplete = false).apply { sendException = RuntimeException("kafka nede") }
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            shouldThrow<IllegalStateException> {
                vedtakService(
                    bucketStorage,
                    RefusjonProducer(feilendeProducer, TEST_TOPIC),
                    arkivService = arkivService,
                    repository = repository,
                ).behandle(melding)
            }

            val refusjonsutfallId = checkNotNull(repository.hent(melding.behandlingId)).refusjonUtfallId
            repository.hentJournalpostId(refusjonsutfallId) shouldBe JOURNALPOST_ID
            val producer = mockProducer()

            vedtakService(
                bucketStorage,
                RefusjonProducer(producer, TEST_TOPIC),
                arkivService = arkivService,
                repository = repository,
            ).behandle(melding)

            coVerify(exactly = 1) { arkivService.arkiver(any(), pdfBytes) }
            verify(exactly = 1) { repository.lagreJournalpostId(refusjonsutfallId, JOURNALPOST_ID) }
            producer.history() shouldHaveSize 1
        }

        test("feiler før publisering når lagring av journalpostId feiler og arkiverer på nytt ved neste forsøk") {
            val bucketStorage = FakeBucketStorage()
            val repository = utfallRepositoryMock()
            val arkivService = arkivServiceMock()
            val producer = mockProducer()
            every { repository.lagreJournalpostId(any(), any()) } throws RuntimeException("db nede")
            mockPdfgen(HttpStatusCode.OK, pdfBytes)
            val service =
                vedtakService(
                    bucketStorage,
                    RefusjonProducer(producer, TEST_TOPIC),
                    arkivService = arkivService,
                    repository = repository,
                )

            shouldThrow<IllegalStateException> { service.behandle(melding) }

            producer.history().shouldBeEmpty()
            val refusjonsutfallId = checkNotNull(repository.hent(melding.behandlingId)).refusjonUtfallId
            repository.hentJournalpostId(refusjonsutfallId) shouldBe null

            every { repository.lagreJournalpostId(refusjonsutfallId, JOURNALPOST_ID) } returns Unit
            service.behandle(melding)

            coVerify(exactly = 2) { arkivService.arkiver(any(), pdfBytes) }
            producer.history() shouldHaveSize 1
        }

        test("feiler og publiserer ingenting når arkivering feiler") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)
            val arkivService =
                mockk<ArkivService> {
                    coEvery { arkiver(any(), any()) } throws RuntimeException("dokarkiv nede")
                }

            shouldThrow<IllegalStateException> {
                vedtakService(bucketStorage, RefusjonProducer(mockProducer, TEST_TOPIC), arkivService = arkivService).behandle(melding)
            }

            bucketStorage.pdfer.size shouldBe 1
            mockProducer.history().shouldBeEmpty()
        }

        test("feiler og lagrer ingenting når pdfgen feiler") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            mockPdfgen(HttpStatusCode.InternalServerError, "Error".toByteArray())

            shouldThrow<IllegalStateException> {
                vedtakService(bucketStorage, RefusjonProducer(mockProducer, TEST_TOPIC)).behandle(melding)
            }

            bucketStorage.pdfer.size shouldBe 0
            mockProducer.history().shouldBeEmpty()
        }

        test("feiler og gjør ingenting mer når virksomhetsnavn ikke finnes") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            shouldThrow<IllegalStateException> {
                vedtakService(
                    bucketStorage,
                    RefusjonProducer(mockProducer, TEST_TOPIC),
                    virksomhetsnavnKlient = virksomhetsnavnKlientMock(navn = null),
                ).behandle(melding)
            }

            bucketStorage.pdfer.size shouldBe 0
            mockProducer.history().shouldBeEmpty()
        }

        test("feiler og gjør ingenting mer når henting av virksomhetsnavn feiler") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)
            val klient =
                mockk<VirksomhetsnavnKlient> {
                    coEvery { hentVirksomhetsnavn(ORGNR) } throws RuntimeException("brreg nede")
                }

            shouldThrow<IllegalStateException> {
                vedtakService(bucketStorage, RefusjonProducer(mockProducer, TEST_TOPIC), virksomhetsnavnKlient = klient).behandle(melding)
            }

            bucketStorage.pdfer.size shouldBe 0
            mockProducer.history().shouldBeEmpty()
        }

        test("feiler og gjør ingenting mer når sykmeldtnavn ikke finnes") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            shouldThrow<IllegalStateException> {
                vedtakService(
                    bucketStorage,
                    RefusjonProducer(mockProducer, TEST_TOPIC),
                    pdlService = pdlServiceMock(navn = null),
                ).behandle(melding)
            }

            bucketStorage.pdfer.size shouldBe 0
            mockProducer.history().shouldBeEmpty()
        }

        test("feiler og gjør ingenting mer når henting av sykmeldtnavn feiler") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)
            val klient =
                mockk<PdlService> {
                    coEvery { hentSykmeldtnavn(FNR) } throws RuntimeException("pdl nede")
                }

            shouldThrow<IllegalStateException> {
                vedtakService(bucketStorage, RefusjonProducer(mockProducer, TEST_TOPIC), pdlService = klient).behandle(melding)
            }

            bucketStorage.pdfer.size shouldBe 0
            mockProducer.history().shouldBeEmpty()
        }
    })
