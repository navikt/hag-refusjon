package no.nav.helsearbeidsgiver.vedtak

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import no.nav.helsearbeidsgiver.db.DatabaseConfig
import no.nav.helsearbeidsgiver.kafka.SisConsumer
import no.nav.helsearbeidsgiver.kafka.SisTolker
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import java.time.LocalDate

class ArbeidstakerVedtakRepositoryTest :
    FunSpec({
        val postgres = PostgreSQLContainer<Nothing>("postgres:18-alpine")
        var databaseConfig: DatabaseConfig? = null
        lateinit var repository: ArbeidstakerVedtakRepository
        val topic = "tbd.sis"
        val partition = TopicPartition(topic, 0)
        val vedtak = arbeidstakerVedtakMeldingMock(Fnr.genererGyldig(), Orgnr.genererGyldig())
        val tolker = SisTolker()

        fun db() = checkNotNull(databaseConfig).database

        fun rader() =
            transaction(db()) {
                ArbeidstakerVedtakEntitet.selectAll().toList()
            }

        fun vedtakJson() = vedtak.toJson(ArbeidstakerVedtakMelding.serializer()).toString()

        fun kafka(vararg values: String): MockConsumer<String, String> =
            MockConsumer<String, String>(OffsetResetStrategy.EARLIEST).apply {
                assign(listOf(partition))
                updateBeginningOffsets(mapOf(partition to 0L))
                values.forEachIndexed { index, value ->
                    addRecord(ConsumerRecord(topic, 0, index.toLong(), "key", value))
                }
            }

        beforeSpec {
            postgres.start()
            databaseConfig = DatabaseConfig(postgres.jdbcUrl, postgres.username, postgres.password)
            repository = ArbeidstakerVedtakRepository(db())
        }

        beforeTest {
            transaction(db()) { ArbeidstakerVedtakEntitet.deleteAll() }
        }

        afterSpec {
            databaseConfig?.close()
            postgres.stop()
        }

        test("lagrer hele modellen som JSONB med søkefelt og refusjonUtfallId") {
            val id = repository.lagre(vedtak, topic, 0, 10L)

            val rad = rader().single()
            rad[ArbeidstakerVedtakEntitet.refusjonUtfallId] shouldBe id
            rad[ArbeidstakerVedtakEntitet.kafkaTopic] shouldBe topic
            rad[ArbeidstakerVedtakEntitet.kafkaPartition] shouldBe 0
            rad[ArbeidstakerVedtakEntitet.kafkaOffset] shouldBe 10L
            rad[ArbeidstakerVedtakEntitet.vedtaksperiodeId] shouldBe vedtak.vedtaksperiodeId
            rad[ArbeidstakerVedtakEntitet.fnr] shouldBe vedtak.foedselsnummer.toString()
            rad[ArbeidstakerVedtakEntitet.orgnr] shouldBe vedtak.organisasjonsnummer.toString()
            rad[ArbeidstakerVedtakEntitet.melding] shouldBe vedtak
            rad[ArbeidstakerVedtakEntitet.opprettet] shouldNotBe null
            transaction(db()) {
                exec("SELECT pg_typeof(melding)::text FROM arbeidstaker_vedtak") { result ->
                    result.next()
                    result.getString(1) shouldBe "jsonb"
                }
            }
        }

        test("redelivery beholder refusjonUtfallId, payload og opprettet") {
            val id = repository.lagre(vedtak, topic, 0, 10L)
            val foersteRad = rader().single()

            repository.lagre(vedtak.copy(sykepengegrunnlag = 1.0), topic, 0, 10L) shouldBe id

            val rad = rader().single()
            rad[ArbeidstakerVedtakEntitet.melding] shouldBe vedtak
            rad[ArbeidstakerVedtakEntitet.opprettet] shouldBe foersteRad[ArbeidstakerVedtakEntitet.opprettet]
        }

        test("beholder flere hendelser for samme vedtaksperiode") {
            val foersteId = repository.lagre(vedtak, topic, 0, 10L)
            val andreId = repository.lagre(vedtak.copy(sykepengegrunnlag = 1.0), topic, 0, 11L)

            andreId shouldNotBe foersteId
            rader() shouldHaveSize 2
        }

        test("samme offset i forskjellige partisjoner og topics er ulike hendelser") {
            val ids =
                setOf(
                    repository.lagre(vedtak, topic, 0, 10L),
                    repository.lagre(vedtak, topic, 1, 10L),
                    repository.lagre(vedtak, "annet.topic", 0, 10L),
                )

            ids.size shouldBe 3
            rader() shouldHaveSize 3
        }

        test("samtidige innsettinger av samme record gir samme refusjonUtfallId") {
            val ids =
                coroutineScope {
                    List(3) {
                        async(Dispatchers.IO) { repository.lagre(vedtak, topic, 0, 10L) }
                    }.awaitAll()
                }

            ids.toSet().size shouldBe 1
            rader() shouldHaveSize 1
        }

        test("lagrer tolket modell uten ukjente felt og med normalisert dagtype") {
            val melding =
                vedtak.copy(
                    utbetalingsdager = listOf(Utbetalingsdag(LocalDate.of(2026, 7, 28), Utbetalingsdagtype.NAV_DAG, 1000)),
                )
            val json =
                melding
                    .toJson(ArbeidstakerVedtakMelding.serializer())
                    .toString()
                    .replace("\"NavDag\"", "\"EnNyDagtype\"")
                    .replaceFirst("{", """{"ekstraFelt":"lagres ikke",""")

            repository.lagre(checkNotNull(tolker.lesMelding(json)), topic, 0, 0L)

            rader()
                .single()[ArbeidstakerVedtakEntitet.melding]
                .utbetalingsdager
                .single()
                .type shouldBe Utbetalingsdagtype.UKJENT_DAG
            transaction(db()) {
                exec("SELECT melding::text FROM arbeidstaker_vedtak") { result ->
                    result.next()
                    result.getString(1).contains("ekstraFelt") shouldBe false
                }
            }
        }

        test("andre databasefeil behandles ikke som duplikater") {
            transaction(db()) {
                exec("ALTER TABLE arbeidstaker_vedtak ADD CONSTRAINT test_offset CHECK (kafka_offset >= 0)")
            }
            try {
                shouldThrow<ExposedSQLException> {
                    repository.lagre(vedtak, topic, 0, -1L)
                }
                rader() shouldHaveSize 0
            } finally {
                transaction(db()) {
                    exec("ALTER TABLE arbeidstaker_vedtak DROP CONSTRAINT test_offset")
                }
            }
        }

        test("databasefeil midt i en batch committer ikke og replay beholder tidligere lagrede UUIDer") {
            val feilendeRepository = spyk(repository)
            every { feilendeRepository.lagre(any(), topic, 0, 1L) } throws IllegalStateException("Databasefeil")
            val foerste = kafka(vedtakJson(), vedtakJson())

            shouldThrow<IllegalStateException> {
                SisConsumer(foerste, topic, tolker, feilendeRepository).poll()
            }
            foerste.committed(setOf(partition))[partition] shouldBe null
            val id = rader().single()[ArbeidstakerVedtakEntitet.refusjonUtfallId]
            foerste.close()

            val replay = kafka(vedtakJson(), vedtakJson())
            SisConsumer(replay, topic, tolker, repository).poll()

            val lagrede = rader()
            lagrede shouldHaveSize 2
            lagrede.single { it[ArbeidstakerVedtakEntitet.kafkaOffset] == 0L }[ArbeidstakerVedtakEntitet.refusjonUtfallId] shouldBe id
            replay.committed(setOf(partition))[partition]?.offset() shouldBe 2L
            replay.close()
        }

        test("ugyldig melding etter lagring forhindrer commit og kan forsøkes igjen uten duplikater") {
            val foerste = kafka(vedtakJson(), "ikke json")
            shouldThrow<SerializationException> {
                SisConsumer(foerste, topic, tolker, repository).poll()
            }
            foerste.committed(setOf(partition))[partition] shouldBe null
            val id = rader().single()[ArbeidstakerVedtakEntitet.refusjonUtfallId]
            foerste.close()

            val replay = kafka(vedtakJson(), "ikke json")
            shouldThrow<SerializationException> {
                SisConsumer(replay, topic, tolker, repository).poll()
            }
            rader().single()[ArbeidstakerVedtakEntitet.refusjonUtfallId] shouldBe id
            replay.committed(setOf(partition))[partition] shouldBe null
            replay.close()
        }

        test("commit-feil etter lagring tåler replay uten å endre refusjonUtfallId") {
            val foerste = spyk(kafka(vedtakJson()))
            every { foerste.commitSync() } throws CommitFailedException("Rebalansering")

            shouldThrow<CommitFailedException> {
                SisConsumer(foerste, topic, tolker, repository).poll()
            }
            foerste.committed(setOf(partition))[partition] shouldBe null
            val id = rader().single()[ArbeidstakerVedtakEntitet.refusjonUtfallId]
            foerste.close()

            val replay = kafka(vedtakJson())
            SisConsumer(replay, topic, tolker, repository).poll()

            rader().single()[ArbeidstakerVedtakEntitet.refusjonUtfallId] shouldBe id
            replay.committed(setOf(partition))[partition]?.offset() shouldBe 1L
            replay.close()
        }
    })
