package no.nav.helsearbeidsgiver.refusjon

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

open class RefusjonUtfallRepository(
    private val db: Database,
) {
    open fun hent(behandlingId: Uuid): RefusjonUtfall? =
        transaction(db) {
            maxAttempts = 1
            queryTimeout = 10

            RefusjonUtfallEntitet
                .selectAll()
                .where { RefusjonUtfallEntitet.behandlingId eq behandlingId }
                .singleOrNull()
                ?.get(RefusjonUtfallEntitet.utfall)
        }

    open fun lagre(
        behandlingId: Uuid,
        utfall: RefusjonUtfall,
    ): Boolean =
        transaction(db) {
            maxAttempts = 1
            queryTimeout = 10

            RefusjonUtfallEntitet
                .insertIgnore {
                    it[this.behandlingId] = behandlingId
                    it[refusjonUtfallId] = utfall.refusjonUtfallId
                    it[vedtaksperiodeId] = utfall.vedtaksperiodeId
                    it[this.utfall] = utfall
                }.insertedCount > 0
        }
}
