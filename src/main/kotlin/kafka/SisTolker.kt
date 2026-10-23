package no.nav.helsearbeidsgiver.kafka

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakMelding
import no.nav.helsearbeidsgiver.refusjon.SisEventName
import no.nav.helsearbeidsgiver.refusjon.Yrkesaktivitetstype
import no.nav.helsearbeidsgiver.utils.json.fromJson

class SisTolker {
    fun lesMelding(melding: String): ArbeidstakerVedtakMelding? {
        val meldingstype = melding.fromJson(Meldingstype.serializer())
        // Meldinger uten eventName er behandlingstatus-meldinger som vi ikke er interessert i.
        if (meldingstype.eventName != SisEventName.VEDTAK_FATTET) {
            return null
        }

        val yrkesaktivitetstype = requireNotNull(meldingstype.yrkesaktivitetstype) { "Vedtak mangler yrkesaktivitetstype" }
        if (yrkesaktivitetstype != Yrkesaktivitetstype.ARBEIDSTAKER) {
            return null
        }

        return melding.fromJson(ArbeidstakerVedtakMelding.serializer())
    }
}

@Serializable
private data class Meldingstype(
    val eventName: SisEventName? = null,
    val yrkesaktivitetstype: Yrkesaktivitetstype? = null,
)
