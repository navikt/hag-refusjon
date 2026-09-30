package no.nav.helsearbeidsgiver.kafka

import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.vedtak.RefusjonUtfall
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID

class RefusjonProducer(
    private val kafkaProducer: Producer<String, String>,
    private val topic: String,
) {
    fun send(
        vedtaksperiodeId: UUID,
        refusjonUtfall: RefusjonUtfall,
    ) {
        val key = vedtaksperiodeId.toString()
        val message = refusjonUtfall.toJson(RefusjonUtfall.serializer())

        runCatching {
            kafkaProducer.send(ProducerRecord(topic, key, message.toString())).get()
        }.onSuccess {
            sikkerLogger().info("Publiserte melding på topic $topic med key $key:\n${message.toPretty()}")
        }.getOrElse {
            sikkerLogger().error("Klarte ikke publisere melding på topic $topic med key $key:\n${message.toPretty()}", it)
            throw it
        }
    }
}
