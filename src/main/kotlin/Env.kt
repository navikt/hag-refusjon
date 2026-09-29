package no.nav.helsearbeidsgiver

object Env {
    fun getPropertyOrNull(prop: String): String? = System.getenv(prop)
}
