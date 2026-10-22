package no.nav.helsearbeidsgiver.refusjonskrav

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.utils.json.jsonConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object RefusjonskravEntitet : Table("refusjonskrav_fra_im") {
    val inntektsmeldingId = uuid("inntektsmelding_id")
    val refusjon = jsonb("refusjon", jsonConfig, Refusjon.serializer()).nullable()
    val opprettet = timestampWithTimeZone("opprettet")

    override val primaryKey = PrimaryKey(inntektsmeldingId)
}
