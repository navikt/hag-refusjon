package no.nav.helsearbeidsgiver.refusjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.db.DatabaseConfig
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.uuid.toKotlinUuid

class RefusjonUtfallRepositoryTest :
    FunSpec({
        val postgres = PostgreSQLContainer<Nothing>("postgres:18-alpine")
        var databaseConfig: DatabaseConfig? = null
        lateinit var repository: RefusjonUtfallRepository
        val melding = gyldigMelding(FNR, ORGNR).parseJson().fromJson(ArbeidstakerVedtakMelding.serializer())
        val utfall = melding.tilRefusjonUtfall(UUID.randomUUID().toKotlinUuid(), emptyList(), ARBEIDSGIVER_NAVN, SYKMELDT_NAVN)

        fun db() = checkNotNull(databaseConfig).database

        beforeSpec {
            postgres.start()
            databaseConfig = DatabaseConfig(postgres.jdbcUrl, postgres.username, postgres.password)
            repository = RefusjonUtfallRepository(db())
        }

        beforeTest {
            transaction(db()) { RefusjonUtfallEntitet.deleteAll() }
        }

        afterSpec {
            databaseConfig?.close()
            postgres.stop()
        }

        test("hent returnerer null når utfall ikke finnes") {
            repository.hent(melding.behandlingId) shouldBe null
        }

        test("lagrer og henter hele utfallet") {
            repository.lagre(melding.behandlingId, utfall) shouldBe true

            repository.hent(melding.behandlingId) shouldBe utfall
            transaction(db()) {
                RefusjonUtfallEntitet.selectAll().single()[RefusjonUtfallEntitet.refusjonUtfallId] shouldBe utfall.refusjonUtfallId
            }
        }

        test("beholder første utfall når samme behandling lagres på nytt") {
            repository.lagre(melding.behandlingId, utfall) shouldBe true

            repository.lagre(melding.behandlingId, utfall.copy(refusjonUtfallId = UUID.randomUUID().toKotlinUuid())) shouldBe false

            repository.hent(melding.behandlingId) shouldBe utfall
        }
    })
