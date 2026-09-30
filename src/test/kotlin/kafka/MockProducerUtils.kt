package no.nav.helsearbeidsgiver.kafka

import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer

const val TEST_TOPIC = "helsearbeidsgiver.refusjon"

fun mockProducer(autoComplete: Boolean = true): MockProducer<String, String> =
    MockProducer(autoComplete, null, StringSerializer(), StringSerializer())
