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
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import no.nav.helsearbeidsgiver.vedtak.ArbeidstakerVedtakMelding
import no.nav.helsearbeidsgiver.vedtak.ArbeidstakerVedtakRepository
import no.nav.helsearbeidsgiver.vedtak.Utbetalingsdag
import no.nav.helsearbeidsgiver.vedtak.Utbetalingsdagtype
import no.nav.helsearbeidsgiver.vedtak.Yrkesaktivitetstype
import no.nav.helsearbeidsgiver.vedtak.arbeidstakerVedtakMeldingMock
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class SisConsumerTest :
    FunSpec({
        val topic = "tbd.sis"
        val tolker = SisTolker()
        val vedtak = arbeidstakerVedtakMeldingMock(Fnr.genererGyldig(), Orgnr.genererGyldig())

        fun vedtakJson(melding: ArbeidstakerVedtakMelding = vedtak) = melding.toJson(ArbeidstakerVedtakMelding.serializer()).toString()

        test("tolker leser vedtak_fattet for arbeidstaker") {
            tolker.lesMelding(vedtakJson()) shouldBe vedtak
        }

        test("tolker ignorerer vedtak for andre yrkesaktivitetstyper") {
            tolker.lesMelding(vedtakJson(vedtak.copy(yrkesaktivitetstype = Yrkesaktivitetstype.FRILANS))) shouldBe null
        }

        test("tolker ignorerer andre yrkesaktivitetstyper før den leser arbeidstakerspesifikke felt") {
            tolker.lesMelding("""{"eventName":"vedtak_fattet","yrkesaktivitetstype":"SELVSTENDIG"}""") shouldBe null
        }

        test("tolker avviser vedtak uten yrkesaktivitetstype") {
            shouldThrow<IllegalArgumentException> {
                tolker.lesMelding("""{"eventName":"vedtak_fattet"}""")
            }
        }

        test("tolker ignorerer behandlingstatus, med og uten eventName") {
            tolker.lesMelding("""{"eventName":"behandlingstatus","status":"OPPRETTET"}""") shouldBe null
            tolker.lesMelding("""{"status":"OPPRETTET"}""") shouldBe null
        }

        test("tolker mapper ukjent utbetalingsdagtype til UKJENT_DAG") {
            val utbetalingsdag = Utbetalingsdag(LocalDate.of(2026, 7, 28), Utbetalingsdagtype.NAV_DAG, 1000)
            val json =
                vedtakJson(vedtak.copy(utbetalingsdager = listOf(utbetalingsdag)))
                    .replace("\"NavDag\"", "\"EnNyDagtype\"")

            val resultat = tolker.lesMelding(json)

            resultat?.utbetalingsdager?.single()?.type shouldBe Utbetalingsdagtype.UKJENT_DAG
        }

        val partition = TopicPartition(topic, 0)

        fun mockConsumer(vararg values: String?): MockConsumer<String, String> =
            MockConsumer<String, String>(OffsetResetStrategy.EARLIEST).apply {
                assign(listOf(partition))
                updateBeginningOffsets(mapOf(partition to 0L))
                values.forEachIndexed { index, value ->
                    addRecord(ConsumerRecord(topic, 0, index.toLong(), "key", value))
                }
            }

        test("consumer lagrer vedtak før den committer offset") {
            val kafka = mockConsumer(vedtakJson())
            val repository = mockk<ArbeidstakerVedtakRepository>()
            every { repository.lagre(vedtak, topic, 0, 0L) } answers {
                kafka.committed(setOf(partition))[partition] shouldBe null
                UUID.randomUUID()
            }

            SisConsumer(kafka, topic, tolker, repository).poll()

            verify(exactly = 1) { repository.lagre(vedtak, topic, 0, 0L) }
            kafka.committed(setOf(partition))[partition]?.offset() shouldBe 1L
            kafka.close()
        }

        test("consumer ignorerer andre hendelser og nullverdier uten å lagre") {
            val kafka =
                mockConsumer(
                    """{"status":"OPPRETTET"}""",
                    vedtakJson(vedtak.copy(yrkesaktivitetstype = Yrkesaktivitetstype.FRILANS)),
                    null,
                )
            val repository = mockk<ArbeidstakerVedtakRepository>()

            SisConsumer(kafka, topic, tolker, repository).poll()

            verify(exactly = 0) { repository.lagre(any(), any(), any(), any()) }
            kafka.committed(setOf(partition))[partition]?.offset() shouldBe 3L
            kafka.close()
        }

        test("consumer stopper på ugyldig JSON uten å committe eller behandle neste melding") {
            val kafka = mockConsumer("ikke json", vedtakJson())
            val repository = mockk<ArbeidstakerVedtakRepository>()

            shouldThrow<SerializationException> {
                SisConsumer(kafka, topic, tolker, repository).poll()
            }

            verify(exactly = 0) { repository.lagre(any(), any(), any(), any()) }
            kafka.committed(setOf(partition))[partition] shouldBe null
            kafka.close()
        }

        test("consumer stopper på databasefeil uten å committe eller behandle neste melding") {
            val kafka = mockConsumer(vedtakJson(), vedtakJson())
            val repository = mockk<ArbeidstakerVedtakRepository>()
            val failure = IllegalStateException("Databasen er utilgjengelig")
            every { repository.lagre(any(), any(), any(), any()) } throws failure

            shouldThrow<IllegalStateException> {
                SisConsumer(kafka, topic, tolker, repository).poll()
            } shouldBe failure

            verify(exactly = 1) { repository.lagre(vedtak, topic, 0, 0L) }
            verify(exactly = 0) { repository.lagre(any(), any(), any(), 1L) }
            kafka.committed(setOf(partition))[partition] shouldBe null
            kafka.close()
        }

        test("consumer lukker Kafka-klienten når polling feiler") {
            val mockConsumer = MockConsumer<String, String>(OffsetResetStrategy.EARLIEST)
            val failure = KafkaException("Polling feilet")
            mockConsumer.setPollException(failure)

            shouldThrow<KafkaException> {
                SisConsumer(mockConsumer, topic, tolker, mockk()).kjoer()
            } shouldBe failure

            mockConsumer.closed() shouldBe true
        }

        test("consumer lukker Kafka-klienten når commit feiler") {
            val kafka =
                object : MockConsumer<String, String>(OffsetResetStrategy.EARLIEST) {
                    override fun commitSync(): Unit = throw CommitFailedException("Rebalansering")
                }
            kafka.schedulePollTask {
                kafka.rebalance(listOf(partition))
                kafka.updateBeginningOffsets(mapOf(partition to 0L))
                kafka.addRecord(ConsumerRecord(topic, 0, 0L, "key", vedtakJson()))
            }
            val repository = mockk<ArbeidstakerVedtakRepository>()
            every { repository.lagre(any(), any(), any(), any()) } returns UUID.randomUUID()

            shouldThrow<CommitFailedException> {
                SisConsumer(kafka, topic, tolker, repository).kjoer()
            }

            kafka.closed() shouldBe true
        }

        test("consumer lukker Kafka-klienten ved kansellering") {
            val kafka = MockConsumer<String, String>(OffsetResetStrategy.EARLIEST)
            val polled = AtomicBoolean(false)
            kafka.schedulePollTask { polled.set(true) }

            coroutineScope {
                val job = launch(Dispatchers.IO) { SisConsumer(kafka, topic, tolker, mockk()).kjoer() }
                try {
                    withTimeout(5_000) {
                        while (!polled.get()) yield()
                    }
                } finally {
                    job.cancelAndJoin()
                }
            }

            kafka.closed() shouldBe true
        }
    })
