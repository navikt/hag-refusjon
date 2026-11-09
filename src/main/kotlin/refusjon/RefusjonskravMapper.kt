package no.nav.helsearbeidsgiver.refusjon

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import java.time.LocalDate
import kotlin.uuid.Uuid

// Bygger refusjonskravet ut fra kravet som gjelder hver dag i daglista. Dager uten refusjonskrav hoppes over.
// Første dag med krav gir beløp per måned, og hver dag der beløpet endres gir en endring med dagens dato som startdato.
fun List<Utbetalingsdag>.tilRefusjonskrav(refusjonskravPerInntektsmelding: Map<Uuid, Refusjon?>): Refusjonskrav? {
    val beloepPerDato =
        sortedBy { it.dato }
            .mapNotNull { dag -> dag.refusjonskravPerMaaned(refusjonskravPerInntektsmelding)?.let { dag.dato to it } }

    val (_, beloepPerMaaned) = beloepPerDato.firstOrNull() ?: return null

    return Refusjonskrav(
        beloepPerMaaned = beloepPerMaaned,
        endringer =
            beloepPerDato
                .zipWithNext()
                .filter { (forrige, neste) -> forrige.second != neste.second }
                .map { (_, neste) -> RefusjonskravEndring(startdato = neste.first, beloep = neste.second) },
    )
}

// Refusjonskrav per måned fra inntektsmeldingen dagen peker på. Null hvis dagen ikke peker på en inntektsmelding med refusjonskrav.
fun Utbetalingsdag.refusjonskravPerMaaned(refusjonskravPerInntektsmelding: Map<Uuid, Refusjon?>): Double? =
    forespurtRefusjon.hagInntektsmeldingReferanse
        ?.let { refusjonskravPerInntektsmelding[it] }
        ?.beloepPaaDato(dato)

// Endringer i refusjonskravet gjelder fra og med startdato og erstatter beløpPerMåned.
fun Refusjon.beloepPaaDato(dato: LocalDate): Double =
    endringer
        .filter { it.startdato <= dato }
        .maxByOrNull { it.startdato }
        ?.beloep
        ?: beloepPerMaaned
