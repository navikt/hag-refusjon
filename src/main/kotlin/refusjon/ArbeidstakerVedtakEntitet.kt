package no.nav.helsearbeidsgiver.refusjon

import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object ArbeidstakerVedtakEntitet : Table("arbeidstaker_vedtak") {
    val behandlingId = uuid("behandling_id")
    val vedtaksperiodeId = uuid("vedtaksperiode_id")
    val fnr = text("fnr")
    val orgnr = text("orgnr")
    val melding = jsonb("melding", jsonConfig, ArbeidstakerVedtakMelding.serializer())
    val opprettet = timestampWithTimeZone("opprettet")

    override val primaryKey = PrimaryKey(behandlingId)
}
