package no.nav.helsearbeidsgiver.refusjon

import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.helsearbeidsgiver.bakgrunnsjobb.ArbeidstakerVedtakJobb
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class VedtakMottak(
    private val arbeidstakerVedtakRepository: ArbeidstakerVedtakRepository,
    private val bakgrunnsjobbService: BakgrunnsjobbService,
    private val db: Database,
) {
    /** Returnerer false dersom vedtaket allerede er lagret. */
    fun motta(melding: ArbeidstakerVedtakMelding): Boolean =
        // Repositoryenes egne transaction(db) går inn i denne, slik at vedtak og jobb lagres atomisk.
        transaction(db) {
            maxAttempts = 1
            queryTimeout = 10

            arbeidstakerVedtakRepository.lagre(melding).also { lagret ->
                if (lagret) {
                    bakgrunnsjobbService.opprettJobbJson<ArbeidstakerVedtakJobb>(
                        maksAntallForsoek = 10,
                        data = melding.toJson(ArbeidstakerVedtakMelding.serializer()),
                    )
                }
            }
        }
}
