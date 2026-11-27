package no.nav.helsearbeidsgiver.refusjon

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class ArbeidstakerVedtakRepository(
    private val db: Database,
) {
    fun lagre(melding: ArbeidstakerVedtakMelding): Boolean =
        transaction(db) {
            maxAttempts = 1
            queryTimeout = 10

            ArbeidstakerVedtakEntitet
                .insertIgnore {
                    it[behandlingId] = melding.behandlingId
                    it[vedtaksperiodeId] = melding.vedtaksperiodeId
                    it[fnr] = melding.foedselsnummer.toString()
                    it[orgnr] = melding.organisasjonsnummer.toString()
                    it[this.melding] = melding
                }.insertedCount > 0
        }

    /** Statuser for eldre vedtak på samme vedtaksperiode som ikke er ferdig behandlet, i mottaksrekkefølge.
     * Henter først vedtaket som tilhører jobben, for å finne sekvensnummeret. Deretter henter vi alle vedtak med lavere sekvensnummer som ikke er ferdig behandlet. */
    fun hentStatusForEldreUbehandlede(behandlingId: Uuid): List<ArbeidstakerVedtakStatus> =
        transaction(db) {
            maxAttempts = 1
            queryTimeout = 10

            val vedtak =
                ArbeidstakerVedtakEntitet
                    .select(ArbeidstakerVedtakEntitet.vedtaksperiodeId, ArbeidstakerVedtakEntitet.sekvensnummer)
                    .where { ArbeidstakerVedtakEntitet.behandlingId eq behandlingId }
                    .singleOrNull()
                    ?: throw IllegalStateException("Fant ikke arbeidstakervedtak med behandlingId $behandlingId.")

            ArbeidstakerVedtakEntitet
                .select(ArbeidstakerVedtakEntitet.status)
                .where {
                    (ArbeidstakerVedtakEntitet.vedtaksperiodeId eq vedtak[ArbeidstakerVedtakEntitet.vedtaksperiodeId]) and
                        (ArbeidstakerVedtakEntitet.sekvensnummer less vedtak[ArbeidstakerVedtakEntitet.sekvensnummer]) and
                        (ArbeidstakerVedtakEntitet.status neq ArbeidstakerVedtakStatus.BEHANDLET)
                }.orderBy(ArbeidstakerVedtakEntitet.sekvensnummer)
                .map { it[ArbeidstakerVedtakEntitet.status] }
        }

    fun oppdaterStatus(
        behandlingId: Uuid,
        status: ArbeidstakerVedtakStatus,
    ) {
        transaction(db) {
            maxAttempts = 1
            queryTimeout = 10

            val oppdatert =
                ArbeidstakerVedtakEntitet.update({ ArbeidstakerVedtakEntitet.behandlingId eq behandlingId }) {
                    it[this.status] = status
                }
            check(oppdatert == 1) { "Fant ikke arbeidstakervedtak med behandlingId $behandlingId ved oppdatering av status." }
        }
    }
}
