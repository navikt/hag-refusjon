package no.nav.helsearbeidsgiver.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer

class RefusjonProducerTest :
    FunSpec({
        val topic = "helsearbeidsgiver.refusjon"
        val melding = buildJsonObject { put("felt", JsonPrimitive("verdi")) }

        test("sender melding til riktig topic med key og JSON-verdi") {
            val mockProducer = MockProducer(true, null, StringSerializer(), StringSerializer())

            RefusjonProducer(mockProducer, topic).send("nokkel", melding)

            val sendt = mockProducer.history()
            sendt shouldHaveSize 1
            sendt.first().topic() shouldBe topic
            sendt.first().key() shouldBe "nokkel"
            sendt.first().value() shouldBe """{"felt":"verdi"}"""
        }

        test("kaster feil når sending feiler") {
            val mockProducer = MockProducer(false, null, StringSerializer(), StringSerializer())
            mockProducer.sendException = RuntimeException("kafka nede")

            shouldThrow<Exception> {
                RefusjonProducer(mockProducer, topic).send("nokkel", melding)
            }
            mockProducer.history().shouldBeEmpty()
        }
    })
