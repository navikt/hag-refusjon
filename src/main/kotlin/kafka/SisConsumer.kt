package no.nav.helsearbeidsgiver.kafka

import io.ktor.server.application.Application
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nav.helsearbeidsgiver.helsesjekker.ShutDownAppState
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.apache.kafka.clients.consumer.Consumer
import java.time.Duration

class SisConsumer(
    private val consumer: Consumer<String, String>,
    private val topic: String,
    private val tolker: SisTolker,
) {
    private val sikkerLogger = sikkerLogger()

    suspend fun kjoer() {
        try {
            consumer.subscribe(listOf(topic))
            while (currentCoroutineContext().isActive) poll()
        } finally {
            consumer.close()
        }
    }

    fun poll() {
        val records = consumer.poll(Duration.ofSeconds(1))
        records.forEach { record ->
            val value = record.value() ?: return@forEach
            runCatching { tolker.lesMelding(value) }
                .onFailure {
                    sikkerLogger.error(
                        "Klarte ikke å tolke melding på $topic, partition ${record.partition()}, offset ${record.offset()}:\n$value",
                        it,
                    )
                }
        }
        if (!records.isEmpty) consumer.commitSync()
    }
}

fun Application.configureSisConsumer(sisConsumer: SisConsumer) {
    val exceptionHandler =
        CoroutineExceptionHandler { _, exception ->
            "Kafka consumer feilet. Tar ned applikasjonen ved å la helsesjekkene feile.".also {
                logger().error(it)
                sikkerLogger().error(it, exception)
            }
            ShutDownAppState.shutDownApp = true
        }

    launch(Dispatchers.IO + exceptionHandler) { sisConsumer.kjoer() }
}
