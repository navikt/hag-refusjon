package no.nav.helsearbeidsgiver.refusjon

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import no.nav.helsearbeidsgiver.arkiv.ArkivService
import no.nav.helsearbeidsgiver.bucket.FakeBucketStorage
import no.nav.helsearbeidsgiver.kafka.RefusjonProducer
import no.nav.helsearbeidsgiver.kafka.TEST_TOPIC
import no.nav.helsearbeidsgiver.kafka.mockProducer
import no.nav.helsearbeidsgiver.person.PdlService
import no.nav.helsearbeidsgiver.refusjonskrav.RefusjonskravService
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
        ) = VedtakService(
            bucketStorage,
            refusjonProducer,
            virksomhetsnavnKlient,
            pdlService,
            arkivService,
            refusjonskravService,
            repository,
        )

        test("behandle lagrer utfallet og gjenbruker det uten nye oppslag når behandle kalles igjen") {
            val bucketStorage = FakeBucketStorage()
            val repository = utfallRepositoryMock()
            val klient = virksomhetsnavnKlientMock()
            val pdl = pdlServiceMock()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            val foersteId =
                vedtakService(
                    bucketStorage,
                    virksomhetsnavnKlient = klient,
                    pdlService = pdl,
                    repository = repository,
                ).behandle(melding)
            val andreId =
                vedtakService(
                    bucketStorage,
                    virksomhetsnavnKlient = klient,
                    pdlService = pdl,
                    repository = repository,
                ).behandle(melding)

            andreId shouldBe foersteId
            repository.hent(melding.behandlingId)?.refusjonUtfallId shouldBe foersteId
            coVerify(exactly = 1) { klient.hentVirksomhetsnavn(ORGNR) }
            coVerify(exactly = 1) { pdl.hentSykmeldtnavn(FNR) }
        }

        test("gjenbruker lagret PDF fra bucket i stedet for å generere den på nytt") {
            val bucketStorage = FakeBucketStorage()
            val repository = utfallRepositoryMock()
            val arkivService = arkivServiceMock()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            val refusjonsutfallId = vedtakService(bucketStorage, arkivService = arkivService, repository = repository).behandle(melding)

            // Hvis PDF-en ble generert på nytt ville den fått dette innholdet.
            mockPdfgen(HttpStatusCode.OK, "Annet innhold".toByteArray())
            vedtakService(bucketStorage, arkivService = arkivService, repository = repository).behandle(melding)

            bucketStorage.pdfer[refusjonsutfallId] shouldBe pdfBytes
            coVerify(exactly = 2) { arkivService.arkiver(any(), pdfBytes) }
        }

        test("behandle feiler når lagring av utfall feiler") {
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

        test("behandle genererer PDF, lagrer den i bucket, arkiverer og publiserer refusjonsutfall") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            val arkivService = arkivServiceMock()
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            val refusjonsutfallId =
                vedtakService(bucketStorage, RefusjonProducer(mockProducer, TEST_TOPIC), arkivService = arkivService)
                    .behandle(melding)

            bucketStorage.pdfer[refusjonsutfallId] shouldBe pdfBytes

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

        test("behandle feiler og publiserer ingenting når publisering til Kafka feiler") {
            val mockProducer = mockProducer(autoComplete = false).apply { sendException = RuntimeException("kafka nede") }
            mockPdfgen(HttpStatusCode.OK, pdfBytes)

            shouldThrow<IllegalStateException> {
                vedtakService(refusjonProducer = RefusjonProducer(mockProducer, TEST_TOPIC)).behandle(melding)
            }

            mockProducer.history().shouldBeEmpty()
        }

        test("behandle feiler og publiserer ingenting når arkivering feiler") {
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

        test("behandle feiler og lagrer ingenting når pdfgen feiler") {
            val bucketStorage = FakeBucketStorage()
            val mockProducer = mockProducer()
            mockPdfgen(HttpStatusCode.InternalServerError, "Error".toByteArray())

            shouldThrow<IllegalStateException> {
                vedtakService(bucketStorage, RefusjonProducer(mockProducer, TEST_TOPIC)).behandle(melding)
            }

            bucketStorage.pdfer.size shouldBe 0
            mockProducer.history().shouldBeEmpty()
        }

        test("behandle feiler og gjør ingenting mer når virksomhetsnavn ikke finnes") {
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

        test("behandle feiler og gjør ingenting mer når henting av virksomhetsnavn feiler") {
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

        test("behandle feiler og gjør ingenting mer når sykmeldtnavn ikke finnes") {
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

        test("behandle feiler og gjør ingenting mer når henting av sykmeldtnavn feiler") {
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
