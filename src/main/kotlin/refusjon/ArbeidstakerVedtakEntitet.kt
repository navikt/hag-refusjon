package no.nav.helsearbeidsgiver.vedtak

import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb

object ArbeidstakerVedtakEntitet : Table("arbeidstaker_vedtak") {
    val id = long("id").autoIncrement()
    val refusjonUtfallId = uuid("refusjon_utfall_id")
    val kafkaTopic = text("kafka_topic")
    val kafkaPartition = integer("kafka_partition")
    val kafkaOffset = long("kafka_offset")
    val vedtaksperiodeId = uuid("vedtaksperiode_id")
    val fnr = text("fnr")
    val orgnr = text("orgnr")
    val melding = jsonb("melding", jsonConfig, ArbeidstakerVedtakMelding.serializer())
    val opprettet = timestampWithTimeZone("opprettet")

    override val primaryKey = PrimaryKey(id)
}
