package no.nav.helsearbeidsgiver

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.helsearbeidsgiver.Env.getPropertyOrNull
import no.nav.helsearbeidsgiver.Env.isDev
import no.nav.helsearbeidsgiver.arkiv.ArkivService
import no.nav.helsearbeidsgiver.auth.TexasTokenValidator
import no.nav.helsearbeidsgiver.auth.TokenPrincipal
import no.nav.helsearbeidsgiver.auth.TokenValidator
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.bucket.BucketStorage
import no.nav.helsearbeidsgiver.bucket.BucketStorageImpl
import no.nav.helsearbeidsgiver.db.DatabaseConfig
import no.nav.helsearbeidsgiver.helsesjekker.naisRoutes
import no.nav.helsearbeidsgiver.kafka.RefusjonProducer
import no.nav.helsearbeidsgiver.kafka.SisConsumer
import no.nav.helsearbeidsgiver.kafka.SisTolker
import no.nav.helsearbeidsgiver.kafka.configureSisConsumer
import no.nav.helsearbeidsgiver.kafka.createKafkaConsumerConfig
import no.nav.helsearbeidsgiver.kafka.createKafkaProducerConfig
import no.nav.helsearbeidsgiver.person.PdlService
import no.nav.helsearbeidsgiver.refusjonskrav.RefusjonskravRepository
import no.nav.helsearbeidsgiver.refusjonskrav.RefusjonskravService
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import no.nav.helsearbeidsgiver.vedtak.ArbeidstakerVedtakRepository
import no.nav.helsearbeidsgiver.vedtak.pdfRoutes
import no.nav.helsearbeidsgiver.vedtak.vedtakRoutes
import no.nav.helsearbeidsgiver.virksomhet.BrregVirksomhetsnavnKlient
import no.nav.helsearbeidsgiver.virksomhet.VirksomhetsnavnKlient
import no.nav.helsearbeidsgiver.virksomhet.VirksomhetsnavnKlientMedFallback
import org.apache.kafka.clients.consumer.KafkaConsumer
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
    val brregVirksomhetsnavnKlient =
        BrregVirksomhetsnavnKlient(
            BrregClient(
                url = getPropertyOrNull("BRREG_URL").orDefault { throw RuntimeException("BRREG_URL ikke satt") },
                cacheConfig = LocalCache.Config(entryDuration = 1.days, maxEntries = 1_000),
            ),
        )
    val virksomhetsnavnKlient: VirksomhetsnavnKlient =
        if (isDev()) {
            VirksomhetsnavnKlientMedFallback(brregVirksomhetsnavnKlient)
        } else {
            brregVirksomhetsnavnKlient
        }
    val pdlService =
        PdlService(
            url = getPropertyOrNull("PDL_URL").orDefault { throw RuntimeException("PDL_URL ikke satt") },
            scope = getPropertyOrNull("PDL_SCOPE").orDefault { throw RuntimeException("PDL_SCOPE ikke satt") },
            tokenEndpoint = getPropertyOrNull("NAIS_TOKEN_ENDPOINT").orDefault { throw RuntimeException("NAIS_TOKEN_ENDPOINT ikke satt") },
        )
    val arkivService =
        ArkivService(
            url = getPropertyOrNull("DOKARKIV_URL").orDefault { throw RuntimeException("DOKARKIV_URL ikke satt") },
            scope = getPropertyOrNull("DOKARKIV_SCOPE").orDefault { throw RuntimeException("DOKARKIV_SCOPE ikke satt") },
            tokenEndpoint = getPropertyOrNull("NAIS_TOKEN_ENDPOINT").orDefault { throw RuntimeException("NAIS_TOKEN_ENDPOINT ikke satt") },
        )
    val tokenValidator =
        TexasTokenValidator(
            introspectionEndpoint =
                getPropertyOrNull("NAIS_TOKEN_INTROSPECTION_ENDPOINT")
                    .orDefault { throw RuntimeException("NAIS_TOKEN_INTROSPECTION_ENDPOINT ikke satt") },
        )
    DatabaseConfig().use { database ->
        val refusjonskravService =
            RefusjonskravService(
                url = getPropertyOrNull("REFUSJONSKRAV_URL").orDefault { throw RuntimeException("REFUSJONSKRAV_URL ikke satt") },
                scope = getPropertyOrNull("REFUSJONSKRAV_SCOPE").orDefault { throw RuntimeException("REFUSJONSKRAV_SCOPE ikke satt") },
                tokenEndpoint =
                    getPropertyOrNull(
                        "NAIS_TOKEN_ENDPOINT",
                    ).orDefault { throw RuntimeException("NAIS_TOKEN_ENDPOINT ikke satt") },
                repository = RefusjonskravRepository(database.database),
            )
        val sisConsumer =
            SisConsumer(
                consumer =
                    KafkaConsumer(
                        createKafkaConsumerConfig(
                            consumerName = "sis-consumer",
                            groupId =
                                getPropertyOrNull(
                                    "KAFKA_CONSUMER_GROUP_ID",
                                ).orDefault { throw RuntimeException("KAFKA_CONSUMER_GROUP_ID ikke satt") },
                        ),
                    ),
                topic = getPropertyOrNull("KAFKA_TOPIC_SIS").orDefault { throw RuntimeException("KAFKA_TOPIC_SIS ikke satt") },
                tolker = SisTolker(),
                repository = ArbeidstakerVedtakRepository(database.database),
            )
        embeddedServer(
            factory = Netty,
            port = 8080,
            module = {
                module(
                    bucketStorage,
                    refusjonProducer,
                    virksomhetsnavnKlient,
                    pdlService,
                    arkivService,
                    tokenValidator,
                    refusjonskravService,
                )
                configureSisConsumer(sisConsumer)
            },
        ).start(wait = true)
    }
}

fun Application.module(
    bucketStorage: BucketStorage,
    refusjonProducer: RefusjonProducer,
    virksomhetsnavnKlient: VirksomhetsnavnKlient,
    pdlService: PdlService,
    arkivService: ArkivService,
    tokenValidator: TokenValidator,
    refusjonskravService: RefusjonskravService,
) {
    install(ContentNegotiation) {
        json(jsonConfig)
    }
    install(Authentication) {
        bearer(ENTRA_ID_AUTH) {
            authenticate { credential ->
                tokenValidator.valider(credential.token)?.let(::TokenPrincipal)
            }
        }
    }
    routing {
        naisRoutes()
        vedtakRoutes(bucketStorage, refusjonProducer, virksomhetsnavnKlient, pdlService, arkivService, refusjonskravService)
        authenticate(ENTRA_ID_AUTH) {
            pdfRoutes(bucketStorage)
        }
    }
}

private const val ENTRA_ID_AUTH = "entra-id"
