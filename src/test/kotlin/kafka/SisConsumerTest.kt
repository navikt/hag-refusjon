package no.nav.helsearbeidsgiver.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.SerializationException
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakMelding
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakRepository
import no.nav.helsearbeidsgiver.refusjon.Avsender
import no.nav.helsearbeidsgiver.refusjon.Utbetalingsdagtype
import no.nav.helsearbeidsgiver.refusjon.Yrkesaktivitetstype
import no.nav.helsearbeidsgiver.refusjon.arbeidstakerVedtakMeldingMock
import no.nav.helsearbeidsgiver.refusjon.utbetalingsdagMock
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

class SisConsumerTest :
    FunSpec({
        val topic = "tbd.sis"
        val tolker = SisTolker()
        val vedtak = arbeidstakerVedtakMeldingMock(Fnr.genererGyldig(), Orgnr.genererGyldig())

        fun vedtakJson(melding: ArbeidstakerVedtakMelding = vedtak) = melding.toJson(ArbeidstakerVedtakMelding.serializer()).toString()

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

        val partition = TopicPartition(topic, 0)

        fun mockConsumer(vararg values: String?): MockConsumer<String, String> =
            MockConsumer<String, String>("earliest").apply {
                assign(listOf(partition))
                updateBeginningOffsets(mapOf(partition to 0L))
                values.forEachIndexed { index, value ->
                    addRecord(ConsumerRecord(topic, 0, index.toLong(), "key", value))
                }
            }

        test("lagrer vedtak før offset bekreftes") {
            val kafka = mockConsumer(vedtakJson())
            val repository = mockk<ArbeidstakerVedtakRepository>()
            every { repository.lagre(vedtak) } answers {
                kafka.committed(setOf(partition))[partition] shouldBe null
                true
            }

            SisConsumer(kafka, topic, tolker, repository).poll()

            verify(exactly = 1) { repository.lagre(vedtak) }
            kafka.committed(setOf(partition))[partition]?.offset() shouldBe 1L
            kafka.close()
        }

        test("hopper over irrelevante meldinger og nullverdier") {
            val kafka =
                mockConsumer(
                    """{"status":"OPPRETTET"}""",
                    vedtakJson(vedtak.copy(yrkesaktivitetstype = Yrkesaktivitetstype.FRILANS)),
                    null,
                )
            val repository = mockk<ArbeidstakerVedtakRepository>()

            SisConsumer(kafka, topic, tolker, repository).poll()

            verify(exactly = 0) { repository.lagre(any()) }
            kafka.committed(setOf(partition))[partition]?.offset() shouldBe 3L
            kafka.close()
        }

        test("stopper ved ugyldig JSON uten å bekrefte offset") {
            val kafka = mockConsumer("ikke json", vedtakJson())
            val repository = mockk<ArbeidstakerVedtakRepository>()

            shouldThrow<SerializationException> {
                SisConsumer(kafka, topic, tolker, repository).poll()
            }

            verify(exactly = 0) { repository.lagre(any()) }
            kafka.committed(setOf(partition))[partition] shouldBe null
            kafka.close()
        }

        test("stopper ved databasefeil uten å bekrefte offset") {
            val kafka = mockConsumer(vedtakJson(), vedtakJson())
            val repository = mockk<ArbeidstakerVedtakRepository>()
            val failure = IllegalStateException("Databasen er utilgjengelig")
            every { repository.lagre(any()) } throws failure

            shouldThrow<IllegalStateException> {
                SisConsumer(kafka, topic, tolker, repository).poll()
            } shouldBe failure

            verify(exactly = 1) { repository.lagre(any()) }
            kafka.committed(setOf(partition))[partition] shouldBe null
            kafka.close()
        }

        test("lukker Kafka-klienten når henting av meldinger feiler") {
            val mockConsumer = MockConsumer<String, String>("earliest")
            val failure = KafkaException("Polling feilet")
            mockConsumer.setPollException(failure)

            shouldThrow<KafkaException> {
                SisConsumer(mockConsumer, topic, tolker, mockk()).kjoer()
            } shouldBe failure

            mockConsumer.closed() shouldBe true
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
            val repository = mockk<ArbeidstakerVedtakRepository>()
            every { repository.lagre(any()) } returns true

            shouldThrow<CommitFailedException> {
                SisConsumer(kafka, topic, tolker, repository).kjoer()
            }

            kafka.closed() shouldBe true
        }

        test("lukker Kafka-klienten ved kansellering") {
            val kafka = MockConsumer<String, String>("earliest")
            val polled = AtomicBoolean(false)
            kafka.schedulePollTask { polled.set(true) }

            coroutineScope {
                val job = launch(Dispatchers.IO) { SisConsumer(kafka, topic, tolker, mockk()).kjoer() }
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
