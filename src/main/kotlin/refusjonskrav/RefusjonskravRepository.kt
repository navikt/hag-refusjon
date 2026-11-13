package no.nav.helsearbeidsgiver.refusjonskrav

import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class RefusjonskravRepository(
    private val db: Database,
) {
    // Duplikater (samme inntektsmeldingId) ignoreres stille. Returnerer ID-ene som faktisk ble lagret.
    fun lagre(refusjonskrav: List<InntektsmeldingRefusjonskrav>): List<Uuid> =
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
                                it[RefusjonskravEntitet.inntektsmeldingId] = krav.inntektsmeldingId
                                it[RefusjonskravEntitet.refusjon] = krav.refusjonskrav
                            }.insertedCount > 0
                    }.map { it.inntektsmeldingId }
            }
        }

    fun hent(inntektsmeldingIder: List<Uuid>): List<InntektsmeldingRefusjonskrav> =
        if (inntektsmeldingIder.isEmpty()) {
            emptyList()
        } else {
            transaction(db) {
                RefusjonskravEntitet
                    .selectAll()
                    .where { RefusjonskravEntitet.inntektsmeldingId inList inntektsmeldingIder }
                    .map {
                        InntektsmeldingRefusjonskrav(
                            inntektsmeldingId = it[RefusjonskravEntitet.inntektsmeldingId],
                            refusjonskrav = it[RefusjonskravEntitet.refusjon],
                        )
                    }
            }
        }
}
