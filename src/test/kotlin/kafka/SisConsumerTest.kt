package no.nav.helsearbeidsgiver.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.SerializationException
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.hag.utils.bakgrunnsjobb.exposed.ExposedBakgrunnsjobRepository
import no.nav.helsearbeidsgiver.bakgrunnsjobb.ArbeidstakerVedtakJobb
import no.nav.helsearbeidsgiver.bakgrunnsjobb.JOB_TYPE
import no.nav.helsearbeidsgiver.db.DatabaseConfig
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakEntitet
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakMelding
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakRepository
import no.nav.helsearbeidsgiver.refusjon.Avsender
import no.nav.helsearbeidsgiver.refusjon.Utbetalingsdagtype
import no.nav.helsearbeidsgiver.refusjon.Yrkesaktivitetstype
import no.nav.helsearbeidsgiver.refusjon.arbeidstakerVedtakMeldingMock
import no.nav.helsearbeidsgiver.refusjon.utbetalingsdagMock
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.toKotlinUuid

class SisConsumerTest :
    FunSpec({
        val postgres = PostgreSQLContainer<Nothing>("postgres:18-alpine")
        var databaseConfig: DatabaseConfig? = null
        lateinit var repository: ArbeidstakerVedtakRepository
        lateinit var bakgrunnsjobbService: BakgrunnsjobbService
        val topic = "tbd.sis"
        val partition = TopicPartition(topic, 0)
        val vedtak = arbeidstakerVedtakMeldingMock(Fnr.genererGyldig(), Orgnr.genererGyldig())
        val tolker = SisTolker()

        fun db() = checkNotNull(databaseConfig).database

        fun rader() =
            transaction(db()) {
                ArbeidstakerVedtakEntitet.selectAll().toList()
            }

        fun jobber() =
            transaction(db()) {
                val resultat = mutableListOf<Triple<String, Int, String>>()
                exec("SELECT type, maks_forsoek, data::text FROM bakgrunnsjobb") { rs ->
                    while (rs.next()) resultat.add(Triple(rs.getString(1), rs.getInt(2), rs.getString(3)))
                }
                resultat
            }

        fun jobbData(jobb: Triple<String, Int, String>) = jobb.third.fromJson(ArbeidstakerVedtakMelding.serializer())

        fun nyBehandling() = vedtak.copy(behandlingId = UUID.randomUUID().toKotlinUuid())

        fun sisConsumer(
            kafka: MockConsumer<String, String>,
            vedtakRepository: ArbeidstakerVedtakRepository = repository,
            service: BakgrunnsjobbService = bakgrunnsjobbService,
        ) = SisConsumer(kafka, topic, tolker, vedtakRepository, service, db())

        fun feilendeBakgrunnsjobbService(): BakgrunnsjobbService {
            val service = BakgrunnsjobbService(spyk(ExposedBakgrunnsjobRepository(db())))
            service.registrer(ArbeidstakerVedtakJobb())
            every { service.bakgrunnsjobbRepository.save(any()) } throws IllegalStateException("Kunne ikke lagre bakgrunnsjobb")
            return service
        }

        fun vedtakJson(melding: ArbeidstakerVedtakMelding = vedtak) = melding.toJson(ArbeidstakerVedtakMelding.serializer()).toString()

        fun kafka(vararg values: String?): MockConsumer<String, String> =
            MockConsumer<String, String>("earliest").apply {
                assign(listOf(partition))
                updateBeginningOffsets(mapOf(partition to 0L))
                values.forEachIndexed { index, value ->
                    addRecord(ConsumerRecord(topic, 0, index.toLong(), "key", value))
                }
            }

        beforeSpec {
            postgres.start()
            databaseConfig = DatabaseConfig(postgres.jdbcUrl, postgres.username, postgres.password)
            repository = ArbeidstakerVedtakRepository(db())
            bakgrunnsjobbService = BakgrunnsjobbService(ExposedBakgrunnsjobRepository(db()))
            bakgrunnsjobbService.registrer(ArbeidstakerVedtakJobb())
        }

        beforeTest {
            transaction(db()) {
                ArbeidstakerVedtakEntitet.deleteAll()
                exec("DELETE FROM bakgrunnsjobb")
            }
        }

        afterSpec {
            databaseConfig?.close()
            postgres.stop()
        }

        test("leser vedtak for arbeidstakere") {
            tolker.lesMelding(vedtakJson()) shouldBe vedtak
        }

        test("ignorerer vedtak for andre yrkesaktivitetstyper") {
            tolker.lesMelding(vedtakJson(vedtak.copy(yrkesaktivitetstype = Yrkesaktivitetstype.FRILANS))) shouldBe null
        }

        test("ignorerer vedtak for selvstendige uten å kreve felt for arbeidstakere") {
            tolker.lesMelding("""{"eventName":"vedtak_fattet","yrkesaktivitetstype":"SELVSTENDIG"}""") shouldBe null
        }

        test("avviser vedtak uten yrkesaktivitetstype") {
            shouldThrow<IllegalArgumentException> {
                tolker.lesMelding("""{"eventName":"vedtak_fattet"}""")
            }
        }

        test("ignorerer meldinger om behandlingsstatus med og uten eventName") {
            tolker.lesMelding("""{"eventName":"behandlingstatus","status":"OPPRETTET"}""") shouldBe null
            tolker.lesMelding("""{"status":"OPPRETTET"}""") shouldBe null
        }

        test("bruker UKJENT_DAG for ukjente utbetalingsdagtyper") {
            val utbetalingsdag = utbetalingsdagMock()
            val json =
                vedtakJson(vedtak.copy(utbetalingsdager = listOf(utbetalingsdag)))
                    .replace("\"NavDag\"", "\"EnNyDagtype\"")

            val resultat = tolker.lesMelding(json)

            resultat?.utbetalingsdager?.single()?.type shouldBe Utbetalingsdagtype.UKJENT_DAG
        }

        test("mapper ukjent avsender til UKJENT og beholder kjente avsendere") {
            val dager =
                listOf(
                    utbetalingsdagMock(avsender = Avsender.ARBEIDSGIVER),
                    utbetalingsdagMock(avsender = Avsender.SAKSBEHANDLER),
                )
            val json =
                vedtakJson(vedtak.copy(utbetalingsdager = dager))
                    .replace("\"avsender\":\"SAKSBEHANDLER\"", "\"avsender\":\"NOE_NYTT\"")

            tolker
                .lesMelding(json)
                ?.utbetalingsdager
                ?.map { it.forespurtRefusjon.avsender } shouldBe
                listOf(Avsender.ARBEIDSGIVER, Avsender.UKJENT)
        }

        test("avviser vedtak uten behandlingId") {
            val json = vedtakJson().replace("\"behandlingId\"", "\"annenId\"")

            shouldThrow<SerializationException> { tolker.lesMelding(json) }
        }

        test("lagrer vedtak og oppretter bakgrunnsjobb før offset bekreftes") {
            val kafka = kafka(vedtakJson())

            sisConsumer(kafka).poll()

            rader().single()[ArbeidstakerVedtakEntitet.behandlingId] shouldBe vedtak.behandlingId
            val jobb = jobber().single()
            jobb.first shouldBe JOB_TYPE
            jobb.second shouldBe 10
            jobbData(jobb) shouldBe vedtak
            kafka.committed(setOf(partition))[partition]?.offset() shouldBe 1L
            kafka.close()
        }

        test("hopper over irrelevante meldinger og nullverdier") {
            val kafka =
                kafka(
                    """{"status":"OPPRETTET"}""",
                    vedtakJson(vedtak.copy(yrkesaktivitetstype = Yrkesaktivitetstype.FRILANS)),
                    null,
                )

            sisConsumer(kafka).poll()

            rader() shouldHaveSize 0
            jobber() shouldHaveSize 0
            kafka.committed(setOf(partition))[partition]?.offset() shouldBe 3L
            kafka.close()
        }

        test("oppretter ikke bakgrunnsjobb for duplikat, men bekrefter offset") {
            repository.lagre(vedtak) shouldBe true
            val kafka = kafka(vedtakJson())

            sisConsumer(kafka).poll()

            jobber() shouldHaveSize 0
            kafka.committed(setOf(partition))[partition]?.offset() shouldBe 1L
            kafka.close()
        }

        test("oppretter én bakgrunnsjobb per nye vedtak, og ingen for duplikater") {
            val andreVedtak = nyBehandling()
            val kafka = kafka(vedtakJson(), vedtakJson(andreVedtak), vedtakJson())

            sisConsumer(kafka).poll()

            jobber().map { jobbData(it).behandlingId }.toSet() shouldBe setOf(vedtak.behandlingId, andreVedtak.behandlingId)
            jobber() shouldHaveSize 2
            kafka.close()
        }

        test("stopper ved ugyldig JSON uten å bekrefte offset") {
            val kafka = kafka("ikke json", vedtakJson())

            shouldThrow<SerializationException> {
                sisConsumer(kafka).poll()
            }

            rader() shouldHaveSize 0
            jobber() shouldHaveSize 0
            kafka.committed(setOf(partition))[partition] shouldBe null
            kafka.close()
        }

        test("oppretter ikke duplikater ved gjentatte forsøk på å behandle en ugyldig melding") {
            val foerste = kafka(vedtakJson(), "ikke json")
            shouldThrow<SerializationException> {
                sisConsumer(foerste).poll()
            }
            foerste.committed(setOf(partition))[partition] shouldBe null
            rader().single()[ArbeidstakerVedtakEntitet.behandlingId] shouldBe vedtak.behandlingId
            foerste.close()

            val replay = kafka(vedtakJson(), "ikke json")
            shouldThrow<SerializationException> {
                sisConsumer(replay).poll()
            }
            rader().single()[ArbeidstakerVedtakEntitet.behandlingId] shouldBe vedtak.behandlingId
            jobber() shouldHaveSize 1
            replay.committed(setOf(partition))[partition] shouldBe null
            replay.close()
        }

        test("beholder lagrede rader ved nytt forsøk etter databasefeil") {
            val andreVedtak = nyBehandling()
            val feilendeRepository = spyk(repository)
            every { feilendeRepository.lagre(andreVedtak) } throws IllegalStateException("Databasefeil")
            val foerste = kafka(vedtakJson(), vedtakJson(andreVedtak))

            shouldThrow<IllegalStateException> {
                sisConsumer(foerste, feilendeRepository).poll()
            }
            foerste.committed(setOf(partition))[partition] shouldBe null
            rader().single()[ArbeidstakerVedtakEntitet.behandlingId] shouldBe vedtak.behandlingId
            foerste.close()

            val replay = kafka(vedtakJson(), vedtakJson(andreVedtak))
            sisConsumer(replay).poll()

            rader().map { it[ArbeidstakerVedtakEntitet.behandlingId] }.toSet() shouldBe
                setOf(vedtak.behandlingId, andreVedtak.behandlingId)
            jobber() shouldHaveSize 2
            replay.committed(setOf(partition))[partition]?.offset() shouldBe 2L
            replay.close()
        }

        test("ruller tilbake vedtaket og bekrefter ikke offset når opprettelse av bakgrunnsjobb feiler") {
            val kafka = kafka(vedtakJson())

            shouldThrow<IllegalStateException> {
                sisConsumer(kafka, service = feilendeBakgrunnsjobbService()).poll()
            }

            rader() shouldHaveSize 0
            jobber() shouldHaveSize 0
            kafka.committed(setOf(partition))[partition] shouldBe null
            kafka.close()
        }

        test("oppretter bakgrunnsjobb ved ny levering etter at opprettelsen feilet") {
            val foerste = kafka(vedtakJson())
            shouldThrow<IllegalStateException> {
                sisConsumer(foerste, service = feilendeBakgrunnsjobbService()).poll()
            }
            foerste.close()

            val replay = kafka(vedtakJson())
            sisConsumer(replay).poll()

            rader().single()[ArbeidstakerVedtakEntitet.behandlingId] shouldBe vedtak.behandlingId
            jobbData(jobber().single()) shouldBe vedtak
            replay.committed(setOf(partition))[partition]?.offset() shouldBe 1L
            replay.close()
        }

        test("beholder raden ved nytt forsøk etter feil ved bekreftelse av offset") {
            val foerste = spyk(kafka(vedtakJson()))
            every { foerste.commitSync() } throws CommitFailedException("Rebalansering")

            shouldThrow<CommitFailedException> {
                sisConsumer(foerste).poll()
            }
            foerste.committed(setOf(partition))[partition] shouldBe null
            rader().single()[ArbeidstakerVedtakEntitet.behandlingId] shouldBe vedtak.behandlingId
            foerste.close()

            val replay = kafka(vedtakJson())
            sisConsumer(replay).poll()

            rader().single()[ArbeidstakerVedtakEntitet.behandlingId] shouldBe vedtak.behandlingId
            jobber() shouldHaveSize 1
            replay.committed(setOf(partition))[partition]?.offset() shouldBe 1L
            replay.close()
        }

        test("lukker Kafka-klienten når henting av meldinger feiler") {
            val kafka = MockConsumer<String, String>("earliest")
            val failure = KafkaException("Polling feilet")
            kafka.setPollException(failure)

            shouldThrow<KafkaException> {
                sisConsumer(kafka).kjoer()
            } shouldBe failure

            kafka.closed() shouldBe true
        }

        test("lukker Kafka-klienten når bekreftelse av offset feiler") {
            val kafka =
                object : MockConsumer<String, String>("earliest") {
                    override fun commitSync(): Unit = throw CommitFailedException("Rebalansering")
                }
            kafka.schedulePollTask {
                kafka.rebalance(listOf(partition))
                kafka.updateBeginningOffsets(mapOf(partition to 0L))
                kafka.addRecord(ConsumerRecord(topic, 0, 0L, "key", vedtakJson()))
            }

            shouldThrow<CommitFailedException> { sisConsumer(kafka).kjoer() }

            kafka.closed() shouldBe true
        }

        test("lukker Kafka-klienten ved kansellering") {
            val kafka = MockConsumer<String, String>("earliest")
            val polled = AtomicBoolean(false)
            kafka.schedulePollTask { polled.set(true) }

            coroutineScope {
                val job = launch(Dispatchers.IO) { sisConsumer(kafka).kjoer() }
                try {
                    withTimeout(5.seconds) {
                        while (!polled.get()) yield()
                    }
                } finally {
                    job.cancelAndJoin()
                }
            }

            kafka.closed() shouldBe true
        }
    })
