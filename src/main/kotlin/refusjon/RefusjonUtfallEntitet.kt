package no.nav.helsearbeidsgiver.refusjon

import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object RefusjonUtfallEntitet : Table("refusjon_utfall") {
    val behandlingId = uuid("behandling_id")
    val refusjonUtfallId = uuid("refusjon_utfall_id")
    val vedtaksperiodeId = uuid("vedtaksperiode_id")
    val utfall = jsonb("utfall", jsonConfig, RefusjonUtfall.serializer())
    val opprettet = timestampWithTimeZone("opprettet")

    override val primaryKey = PrimaryKey(behandlingId)
}
