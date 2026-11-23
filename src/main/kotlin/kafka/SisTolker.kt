package no.nav.helsearbeidsgiver.kafka

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.refusjon.ArbeidstakerVedtakMelding
import no.nav.helsearbeidsgiver.refusjon.SisEventName
import no.nav.helsearbeidsgiver.refusjon.Yrkesaktivitetstype
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

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
        runCatching {
            return melding.fromJson(ArbeidstakerVedtakMelding.serializer())
        }.onFailure {
            sikkerLogger().error(melding, it)
        }
        return null // Med dette mister vi meldingen, går ikke i crash-loop ved feilparsing
    }
}

@Serializable
private data class Meldingstype(
    val eventName: SisEventName? = null,
    val yrkesaktivitetstype: Yrkesaktivitetstype? = null,
)
