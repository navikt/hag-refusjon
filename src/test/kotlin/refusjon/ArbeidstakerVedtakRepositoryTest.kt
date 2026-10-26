package no.nav.helsearbeidsgiver.refusjon

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
import org.apache.kafka.common.TopicPartition
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.uuid.toKotlinUuid

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

        fun nyBehandling() = vedtak.copy(behandlingId = UUID.randomUUID().toKotlinUuid())

        fun vedtakJson(melding: ArbeidstakerVedtakMelding = vedtak) = melding.toJson(ArbeidstakerVedtakMelding.serializer()).toString()

        fun kafka(vararg values: String): MockConsumer<String, String> =
            MockConsumer<String, String>("earliest").apply {
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

        test("lagrer vedtak som JSONB med vedtaksperiodeId, fnr og orgnr i egne kolonner") {
            repository.lagre(vedtak) shouldBe true

            val rad = rader().single()
            rad[ArbeidstakerVedtakEntitet.behandlingId] shouldBe vedtak.behandlingId
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

        test("beholder innhold og opprettelsestidspunkt når samme behandling lagres på nytt") {
            repository.lagre(vedtak) shouldBe true
            val foersteRad = rader().single()

            repository.lagre(vedtak.copy(sykepengegrunnlag = 1.0)) shouldBe false

            val rad = rader().single()
            rad[ArbeidstakerVedtakEntitet.melding] shouldBe vedtak
            rad[ArbeidstakerVedtakEntitet.opprettet] shouldBe foersteRad[ArbeidstakerVedtakEntitet.opprettet]
        }

        test("lagrer flere behandlinger for samme vedtaksperiode") {
            val andreVedtak = nyBehandling()

            repository.lagre(vedtak) shouldBe true
            repository.lagre(andreVedtak) shouldBe true

            rader().map { it[ArbeidstakerVedtakEntitet.behandlingId] }.toSet() shouldBe
                setOf(vedtak.behandlingId, andreVedtak.behandlingId)
        }

        test("lagrer samme melding bare én gang ved samtidige forsøk") {
            val resultater =
                coroutineScope {
                    List(3) {
                        async(Dispatchers.IO) { repository.lagre(vedtak) }
                    }.awaitAll()
                }

            resultater.count { it } shouldBe 1
            rader() shouldHaveSize 1
        }

        test("utelater ukjente felt og lagrer ukjent dagtype som UKJENT_DAG") {
            val melding =
                vedtak.copy(
                    utbetalingsdager =
                        listOf(
                            utbetalingsdagMock(),
                        ),
                )
            val json =
                melding
                    .toJson(ArbeidstakerVedtakMelding.serializer())
                    .toString()
                    .replace("\"NavDag\"", "\"EnNyDagtype\"")
                    .replaceFirst("{", """{"ekstraFelt":"lagres ikke",""")

            repository.lagre(checkNotNull(tolker.lesMelding(json)))

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

        test("beholder lagrede rader ved nytt forsøk etter databasefeil") {
            val andreVedtak = nyBehandling()
            val feilendeRepository = spyk(repository)
            every { feilendeRepository.lagre(andreVedtak) } throws IllegalStateException("Databasefeil")
            val foerste = kafka(vedtakJson(), vedtakJson(andreVedtak))

            shouldThrow<IllegalStateException> {
                SisConsumer(foerste, topic, tolker, feilendeRepository).poll()
            }
            foerste.committed(setOf(partition))[partition] shouldBe null
            rader().single()[ArbeidstakerVedtakEntitet.behandlingId] shouldBe vedtak.behandlingId
            foerste.close()

            val replay = kafka(vedtakJson(), vedtakJson(andreVedtak))
            SisConsumer(replay, topic, tolker, repository).poll()

            val lagrede = rader()
            lagrede shouldHaveSize 2
            lagrede.map { it[ArbeidstakerVedtakEntitet.behandlingId] }.toSet() shouldBe
                setOf(vedtak.behandlingId, andreVedtak.behandlingId)
            replay.committed(setOf(partition))[partition]?.offset() shouldBe 2L
            replay.close()
        }

        test("oppretter ikke duplikater ved gjentatte forsøk på å behandle en ugyldig melding") {
            val foerste = kafka(vedtakJson(), "ikke json")
            shouldThrow<SerializationException> {
                SisConsumer(foerste, topic, tolker, repository).poll()
            }
            foerste.committed(setOf(partition))[partition] shouldBe null
            rader().single()[ArbeidstakerVedtakEntitet.behandlingId] shouldBe vedtak.behandlingId
            foerste.close()

            val replay = kafka(vedtakJson(), "ikke json")
            shouldThrow<SerializationException> {
                SisConsumer(replay, topic, tolker, repository).poll()
            }
            rader().single()[ArbeidstakerVedtakEntitet.behandlingId] shouldBe vedtak.behandlingId
            replay.committed(setOf(partition))[partition] shouldBe null
            replay.close()
        }

        test("beholder raden ved nytt forsøk etter feil ved bekreftelse av offset") {
            val foerste = spyk(kafka(vedtakJson()))
            every { foerste.commitSync() } throws CommitFailedException("Rebalansering")

            shouldThrow<CommitFailedException> {
                SisConsumer(foerste, topic, tolker, repository).poll()
            }
            foerste.committed(setOf(partition))[partition] shouldBe null
            rader().single()[ArbeidstakerVedtakEntitet.behandlingId] shouldBe vedtak.behandlingId
            foerste.close()

            val replay = kafka(vedtakJson())
            SisConsumer(replay, topic, tolker, repository).poll()

            rader().single()[ArbeidstakerVedtakEntitet.behandlingId] shouldBe vedtak.behandlingId
            replay.committed(setOf(partition))[partition]?.offset() shouldBe 1L
            replay.close()
        }
    })
