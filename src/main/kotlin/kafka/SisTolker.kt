package no.nav.helsearbeidsgiver.kafka

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.vedtak.ArbeidstakerVedtakMelding
import no.nav.helsearbeidsgiver.vedtak.SisEventName
import no.nav.helsearbeidsgiver.vedtak.Yrkesaktivitetstype

class SisTolker {
    private val sikkerLogger = sikkerLogger()

    fun lesMelding(melding: String): ArbeidstakerVedtakMelding? {
        // Meldinger uten eventName er behandlingstatus-meldinger som vi ikke er interessert i.
        if (melding.fromJson(EventNameWrapper.serializer()).eventName != SisEventName.VEDTAK_FATTET) {
            return null
        }

        val vedtak = melding.fromJson(ArbeidstakerVedtakMelding.serializer())
        if (vedtak.yrkesaktivitetstype != Yrkesaktivitetstype.ARBEIDSTAKER) {
            return null
        }

        sikkerLogger.info("Leste arbeidstakervedtak: $vedtak")
        return vedtak
    }
}

@Serializable
private data class EventNameWrapper(
    val eventName: SisEventName? = null,
)
