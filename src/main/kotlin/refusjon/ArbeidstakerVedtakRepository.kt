package no.nav.helsearbeidsgiver.refusjon

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

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
}
