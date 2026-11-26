package no.nav.helsearbeidsgiver.refusjon

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
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

    open fun finnesForAnnenBehandling(
        vedtaksperiodeId: Uuid,
        behandlingId: Uuid,
    ): Boolean =
        transaction(db) {
            maxAttempts = 1
            queryTimeout = 10

            RefusjonUtfallEntitet
                .selectAll()
                .where {
                    (RefusjonUtfallEntitet.vedtaksperiodeId eq vedtaksperiodeId) and
                        (RefusjonUtfallEntitet.behandlingId neq behandlingId)
                }.limit(1)
                .any()
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

    open fun hentJournalpostId(refusjonUtfallId: Uuid): String? =
        transaction(db) {
            maxAttempts = 1
            queryTimeout = 10

            RefusjonUtfallEntitet
                .selectAll()
                .where { RefusjonUtfallEntitet.refusjonUtfallId eq refusjonUtfallId }
                .singleOrNull()
                ?.get(RefusjonUtfallEntitet.journalpostId)
        }

    open fun lagreJournalpostId(
        refusjonUtfallId: Uuid,
        journalpostId: String,
    ) {
        transaction(db) {
            maxAttempts = 1
            queryTimeout = 10

            val oppdatert =
                RefusjonUtfallEntitet.update({ RefusjonUtfallEntitet.refusjonUtfallId eq refusjonUtfallId }) {
                    it[this.journalpostId] = journalpostId
                }
            check(oppdatert == 1) { "Fant ikke refusjonsutfall med refusjonUtfallId $refusjonUtfallId ved lagring av journalpostId." }
        }
    }
}
