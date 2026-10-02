package no.nav.helsearbeidsgiver

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.helsearbeidsgiver.Env.getPropertyOrNull
import no.nav.helsearbeidsgiver.Env.isDev
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.bucket.BucketStorage
import no.nav.helsearbeidsgiver.bucket.BucketStorageImpl
import no.nav.helsearbeidsgiver.helsesjekker.naisRoutes
import no.nav.helsearbeidsgiver.kafka.RefusjonProducer
import no.nav.helsearbeidsgiver.kafka.createKafkaProducerConfig
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import no.nav.helsearbeidsgiver.vedtak.refusjonRoutes
import no.nav.helsearbeidsgiver.virksomhet.BrregVirksomhetsnavnKlient
import no.nav.helsearbeidsgiver.virksomhet.DevVirksomhetsnavnKlient
import no.nav.helsearbeidsgiver.virksomhet.VirksomhetsnavnKlient
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days

fun main() {
    LoggerFactory.getLogger("App").info("Starter server...")
    val bucketStorage =
        BucketStorageImpl(
            bucketName = getPropertyOrNull("GCP_BUCKET_NAME").orDefault { throw RuntimeException("GCP_BUCKET_NAME ikke satt") },
        )
    val refusjonProducer =
        RefusjonProducer(
            kafkaProducer = KafkaProducer(createKafkaProducerConfig(producerName = "refusjon-producer")),
            topic = getPropertyOrNull("KAFKA_TOPIC_REFUSJON").orDefault { throw RuntimeException("KAFKA_TOPIC_REFUSJON ikke satt") },
        )
    val virksomhetsnavnKlient =
        if (isDev()) {
            DevVirksomhetsnavnKlient()
        } else {
            BrregVirksomhetsnavnKlient(
                BrregClient(
                    url = getPropertyOrNull("BRREG_URL").orDefault { throw RuntimeException("BRREG_URL ikke satt") },
                    cacheConfig = LocalCache.Config(entryDuration = 1.days, maxEntries = 1_000),
                ),
            )
        }
    embeddedServer(
        factory = Netty,
        port = 8080,
        module = { module(bucketStorage, refusjonProducer, virksomhetsnavnKlient) },
    ).start(wait = true)
}

fun Application.module(
    bucketStorage: BucketStorage,
    refusjonProducer: RefusjonProducer,
    virksomhetsnavnKlient: VirksomhetsnavnKlient,
) {
    install(ContentNegotiation) {
        json(jsonConfig)
    }
    routing {
        naisRoutes()
        get("/hello") {
            call.respondText("Hello World!")
        }
        refusjonRoutes(bucketStorage, refusjonProducer, virksomhetsnavnKlient)
    }
}
