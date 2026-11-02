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
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.helsearbeidsgiver.bakgrunnsjobb.ArbeidstakerVedtakJobb
import no.nav.helsearbeidsgiver.helsesjekker.ShutDownAppState
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakMelding
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.apache.kafka.clients.consumer.CloseOptions
import org.apache.kafka.clients.consumer.Consumer
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration

class SisConsumer(
    private val consumer: Consumer<String, String>,
    private val topic: String,
    private val tolker: SisTolker,
    private val arbeidstakerVedtakRepository: ArbeidstakerVedtakRepository,
    private val bakgrunnsjobbService: BakgrunnsjobbService,
    private val db: Database,
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

            // Logger denne for enkel inspeksjon mens vi utvikler og tester. Kan fjernes senere.
            sikkerLogger.info("Mottok melding på $topic, partition ${record.partition()}, offset ${record.offset()}: $value")

            runCatching {
                val melding = tolker.lesMelding(value) ?: return@forEach

                // Repositoryenes egne transaction(db) går inn i denne, slik at vedtak og jobb lagres atomisk.
                val meldingLagret =
                    transaction(db) {
                        maxAttempts = 1
                        queryTimeout = 10

                        arbeidstakerVedtakRepository.lagre(melding).also { lagret ->
                            if (lagret) {
                                bakgrunnsjobbService.opprettJobbJson<ArbeidstakerVedtakJobb>(
                                    maksAntallForsoek = 10,
                                    data = melding.toJson(ArbeidstakerVedtakMelding.serializer()),
                                )
                            }
                        }
                    }
                if (meldingLagret) {
                    sikkerLogger.info("Arbeidstakervedtak lagret med behandlingId ${melding.behandlingId}")
                } else {
                    sikkerLogger.info(
                        "Arbeidstakervedtak med behandlingId ${melding.behandlingId} var allerede lagret, ignorerer duplikat",
                    )
                }
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
