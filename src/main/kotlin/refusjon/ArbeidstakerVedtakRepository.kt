package no.nav.helsearbeidsgiver.refusjon

import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.UuidColumnType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class ArbeidstakerVedtakRepository(
    private val db: Database,
) {
    fun lagre(melding: ArbeidstakerVedtakMelding): Uuid =
        transaction(db) {
            maxAttempts = 1
            queryTimeout = 10

            // Konflikt på behandlingId (PK) er en forventet duplikat.
            exec(
                """
                INSERT INTO arbeidstaker_vedtak
                    (behandling_id, vedtaksperiode_id, fnr, orgnr, melding)
                VALUES (?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (behandling_id) DO NOTHING
                """.trimIndent(),
                args =
                    listOf(
                        UuidColumnType() to melding.behandlingId,
                        UuidColumnType() to melding.vedtaksperiodeId,
                        TextColumnType() to melding.foedselsnummer.toString(),
                        TextColumnType() to melding.organisasjonsnummer.toString(),
                        TextColumnType() to jsonConfig.encodeToString(ArbeidstakerVedtakMelding.serializer(), melding),
                    ),
            )

            melding.behandlingId
        }
}
