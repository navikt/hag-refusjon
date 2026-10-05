package no.nav.helsearbeidsgiver.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NavnUtilsTest :
    FunSpec({
        test("giNavnStorForbokstav gir navn stor forbokstav") {
            "OLA NORDMANN".giNavnStorForbokstav() shouldBe "Ola Nordmann"
            "ÅGE ØRRET".giNavnStorForbokstav() shouldBe "Åge Ørret"
            "donald duck".giNavnStorForbokstav() shouldBe "Donald Duck"
            "JAN-ERIK OLA".giNavnStorForbokstav() shouldBe "Jan-Erik Ola"
            // aksepterer at disse edge case "McDonald", "Vincent van Gogh" og "Jeanne d'Arc" ikke får riktig stor forbokstav
            "MCDONALD".giNavnStorForbokstav() shouldBe "Mcdonald"
            "VINCENT VAN GOGH".giNavnStorForbokstav() shouldBe "Vincent Van Gogh"
            "JEANNE D'ARC".giNavnStorForbokstav() shouldBe "Jeanne D'arc"
        }
    })
