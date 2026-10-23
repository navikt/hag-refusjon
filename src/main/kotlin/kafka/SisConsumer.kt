package no.nav.helsearbeidsgiver.kafka

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.helsesjekker.ShutDownAppState
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakRepository
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.apache.kafka.clients.consumer.CloseOptions
import org.apache.kafka.clients.consumer.Consumer
import java.time.Duration

class SisConsumer(
    private val consumer: Consumer<String, String>,
    private val topic: String,
    private val tolker: SisTolker,
    private val repository: ArbeidstakerVedtakRepository,
) {
    private val sikkerLogger = sikkerLogger()

    suspend fun kjoer() {
        try {
            consumer.subscribe(listOf(topic))
            while (currentCoroutineContext().isActive) poll()
        } finally {
            consumer.close(CloseOptions.timeout(Duration.ofSeconds(10)))
        }
    }

    suspend fun poll() {
        val records = consumer.poll(Duration.ofSeconds(1))
        records.forEach { record ->
            currentCoroutineContext().ensureActive()
            val value = record.value() ?: return@forEach
            runCatching {
                val melding = tolker.lesMelding(value) ?: return@forEach
                // TODO: lag bg-jobb
                val behandlingId = repository.lagre(melding)
                sikkerLogger.info("Arbeidstakervedtak lagret med behandlingId $behandlingId")
            }.onFailure {
                sikkerLogger.error(
                    "Klarte ikke å behandle melding på $topic, partition ${record.partition()}, offset ${record.offset()}",
                    it,
                )
            }.getOrThrow()
        }
        currentCoroutineContext().ensureActive()
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

    val job = launch(Dispatchers.IO + exceptionHandler) { sisConsumer.kjoer() }
    monitor.subscribe(ApplicationStopping) {
        runBlocking { job.cancelAndJoin() }
    }
}
