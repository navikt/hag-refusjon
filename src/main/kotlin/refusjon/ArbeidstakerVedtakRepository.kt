package no.nav.helsearbeidsgiver.vedtak

import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.UuidColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class ArbeidstakerVedtakRepository(
    private val db: Database,
) {
    fun lagre(
        melding: ArbeidstakerVedtakMelding,
        topic: String,
        partition: Int,
        offset: Long,
    ): Uuid =
        transaction(db) {
            maxAttempts = 1
            queryTimeout = 10

            // Kun konflikt på Kafka-posisjonen er en forventet duplikat.
            val nyId =
                this
                    .exec(
                        """
                        INSERT INTO arbeidstaker_vedtak
                            (refusjon_utfall_id, kafka_topic, kafka_partition, kafka_offset,
                             vedtaksperiode_id, fnr, orgnr, melding)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        ON CONFLICT (kafka_topic, kafka_partition, kafka_offset) DO NOTHING
                        RETURNING refusjon_utfall_id
                        """.trimIndent(),
                        explicitStatementType = StatementType.SELECT,
                        args =
                            listOf(
                                UuidColumnType() to UUID.randomUUID().toKotlinUuid(),
                                TextColumnType() to topic,
                                IntegerColumnType() to partition,
                                LongColumnType() to offset,
                                UuidColumnType() to melding.vedtaksperiodeId,
                                TextColumnType() to melding.foedselsnummer.toString(),
                                TextColumnType() to melding.organisasjonsnummer.toString(),
                                TextColumnType() to jsonConfig.encodeToString(ArbeidstakerVedtakMelding.serializer(), melding),
                            ),
                    ) { result ->
                        if (result.next()) result.getObject("refusjon_utfall_id", UUID::class.java) else null
                    }?.toKotlinUuid()

            nyId ?: ArbeidstakerVedtakEntitet
                .selectAll()
                .where {
                    (ArbeidstakerVedtakEntitet.kafkaTopic eq topic) and
                        (ArbeidstakerVedtakEntitet.kafkaPartition eq partition) and
                        (ArbeidstakerVedtakEntitet.kafkaOffset eq offset)
                }.single()[ArbeidstakerVedtakEntitet.refusjonUtfallId]
        }
}
