@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.refusjon

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

// Refusjonskravet som gjelder for dagene i vedtaket, strukturert som i inntektsmeldingen.
@Serializable
data class Refusjonskrav(
    val beloepPerMaaned: Double,
    val endringer: List<RefusjonskravEndring>,
)

@Serializable
data class RefusjonskravEndring(
    val startdato: LocalDate,
    val beloep: Double,
)
