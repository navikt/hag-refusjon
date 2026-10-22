package no.nav.helsearbeidsgiver.refusjonskrav

import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class RefusjonskravRepository(
    private val db: Database,
) {
    // Duplikater (samme inntektsmeldingId) ignoreres stille. Returnerer ID-ene som faktisk ble lagret.
    fun lagre(refusjonskrav: List<InntektsmeldingRefusjonskrav>): List<UUID> =
        if (refusjonskrav.isEmpty()) {
            emptyList()
        } else {
            transaction(db) {
                queryTimeout = 10

                refusjonskrav
                    .distinctBy { it.inntektsmeldingId }
                    .filter { krav ->
                        RefusjonskravEntitet
                            .insertIgnore {
                                it[RefusjonskravEntitet.inntektsmeldingId] = krav.inntektsmeldingId.toKotlinUuid()
                                it[RefusjonskravEntitet.refusjon] = krav.refusjonskrav
                            }.insertedCount > 0
                    }.map { it.inntektsmeldingId }
            }
        }

    fun hent(inntektsmeldingIder: List<UUID>): List<InntektsmeldingRefusjonskrav> =
        if (inntektsmeldingIder.isEmpty()) {
            emptyList()
        } else {
            transaction(db) {
                RefusjonskravEntitet
                    .selectAll()
                    .where { RefusjonskravEntitet.inntektsmeldingId inList inntektsmeldingIder.map { it.toKotlinUuid() } }
                    .map {
                        InntektsmeldingRefusjonskrav(
                            inntektsmeldingId = it[RefusjonskravEntitet.inntektsmeldingId].toJavaUuid(),
                            refusjonskrav = it[RefusjonskravEntitet.refusjon],
                        )
                    }
            }
        }
}
