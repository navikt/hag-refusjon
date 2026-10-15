package no.nav.helsearbeidsgiver.kafka

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import no.nav.helsearbeidsgiver.vedtak.ArbeidstakerVedtakMelding
import no.nav.helsearbeidsgiver.vedtak.Utbetalingsdag
import no.nav.helsearbeidsgiver.vedtak.Utbetalingsdagtype
import no.nav.helsearbeidsgiver.vedtak.Yrkesaktivitetstype
import no.nav.helsearbeidsgiver.vedtak.arbeidstakerVedtakMeldingMock
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import java.time.LocalDate

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

        test("consumer committer offset og krasjer ikke på ugyldig JSON") {
            val mockConsumer = MockConsumer<String, String>(OffsetResetStrategy.EARLIEST)
            val partition = TopicPartition(topic, 0)
            mockConsumer.assign(listOf(partition))
            mockConsumer.updateBeginningOffsets(mapOf(partition to 0L))
            mockConsumer.addRecord(ConsumerRecord(topic, 0, 0L, "key1", "ikke json"))
            mockConsumer.addRecord(ConsumerRecord(topic, 0, 1L, "key2", vedtakJson()))

            val consumer = SisConsumer(mockConsumer, topic, tolker)
            shouldNotThrowAny { consumer.poll() }

            mockConsumer.committed(setOf(partition))[partition]?.offset() shouldBe 2L
        }
    })
