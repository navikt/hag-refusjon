package no.nav.helsearbeidsgiver.utils

fun String.giNavnStorForbokstav(): String =
    lowercase()
        .split(" ")
        .joinToString(" ") { ord ->
            ord
                .split("-")
                .joinToString("-") { it.replaceFirstChar(Char::titlecase) }
        }
