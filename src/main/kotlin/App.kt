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
import no.nav.helsearbeidsgiver.bucket.BucketStorage
import no.nav.helsearbeidsgiver.bucket.BucketStorageImpl
import no.nav.helsearbeidsgiver.helsesjekker.naisRoutes
import no.nav.helsearbeidsgiver.kafka.RefusjonProducer
import no.nav.helsearbeidsgiver.kafka.createKafkaProducerConfig
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import no.nav.helsearbeidsgiver.vedtak.refusjonRoutes
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory

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
    embeddedServer(
        factory = Netty,
        port = 8080,
        module = { module(bucketStorage, refusjonProducer) },
    ).start(wait = true)
}

fun Application.module(
    bucketStorage: BucketStorage,
    refusjonProducer: RefusjonProducer,
) {
    install(ContentNegotiation) {
        json(jsonConfig)
    }
    routing {
        naisRoutes()
        get("/hello") {
            call.respondText("Hello World!")
        }
        refusjonRoutes(bucketStorage, refusjonProducer)
    }
}
