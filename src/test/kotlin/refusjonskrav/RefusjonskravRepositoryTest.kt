package no.nav.helsearbeidsgiver.refusjonskrav

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.db.DatabaseConfig
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RefusjonEndring
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import java.time.LocalDate
import java.util.UUID

class RefusjonskravRepositoryTest :
    FunSpec({
        val postgres = PostgreSQLContainer<Nothing>("postgres:18-alpine")
        var databaseConfig: DatabaseConfig? = null
        lateinit var repository: RefusjonskravRepository

        fun db() = checkNotNull(databaseConfig).database

        fun krav(refusjon: Refusjon? = Refusjon(beloepPerMaaned = 1000.0, endringer = emptyList())) =
            InntektsmeldingRefusjonskrav(UUID.randomUUID(), refusjon)

        beforeSpec {
            postgres.start()
            databaseConfig = DatabaseConfig(postgres.jdbcUrl, postgres.username, postgres.password)
            repository = RefusjonskravRepository(db())
        }

        beforeTest {
            transaction(db()) { RefusjonskravEntitet.deleteAll() }
        }

        afterSpec {
            databaseConfig?.close()
            postgres.stop()
        }

        test("lagrer og henter refusjonskrav, også uten refusjon") {
            val medRefusjon = krav()
            val utenRefusjon = krav(refusjon = null)
            val medRefusjonsEndringer =
                krav(
                    refusjon =
                        Refusjon(
                            100.0,
                            endringer =
                                listOf(
                                    RefusjonEndring(500.0, LocalDate.of(2026, 1, 1)),
                                    RefusjonEndring(0.0, LocalDate.of(2026, 2, 2)),
                                ),
                        ),
                )

            repository.lagre(listOf(medRefusjon, utenRefusjon, medRefusjonsEndringer)) shouldContainExactlyInAnyOrder
                listOf(medRefusjon.inntektsmeldingId, utenRefusjon.inntektsmeldingId, medRefusjonsEndringer.inntektsmeldingId)

            repository.hent(
                listOf(medRefusjon.inntektsmeldingId, utenRefusjon.inntektsmeldingId, medRefusjonsEndringer.inntektsmeldingId),
            ) shouldContainExactlyInAnyOrder
                listOf(medRefusjon, utenRefusjon, medRefusjonsEndringer)
        }

        test("duplikat ignoreres uten feil og beholder første rad") {
            val original = krav()
            val ny = krav()
            val duplikat = original.copy(refusjonskrav = null)

            repository.lagre(listOf(original))
            repository.lagre(listOf(duplikat, ny)) shouldBe listOf(ny.inntektsmeldingId)

            transaction(db()) { RefusjonskravEntitet.selectAll().count() } shouldBe 2
            repository.hent(listOf(original.inntektsmeldingId)) shouldBe listOf(original)
        }

        test("duplikater i samme liste lagres kun én gang") {
            val krav = krav()

            repository.lagre(listOf(krav, krav)) shouldBe listOf(krav.inntektsmeldingId)
        }

        test("hent returnerer kun kjente id-er, og tom liste gir tom liste") {
            repository.hent(listOf(UUID.randomUUID())) shouldBe emptyList()
            repository.hent(emptyList()) shouldBe emptyList()
            repository.lagre(emptyList()) shouldBe emptyList()
        }
    })
