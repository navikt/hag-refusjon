package no.nav.helsearbeidsgiver

object Env {
    fun getPropertyOrNull(prop: String): String? = System.getenv(prop)

    fun getProperty(prop: String): String = getPropertyOrNull(prop) ?: throw RuntimeException("$prop ikke satt")

    fun isDev(): Boolean = getPropertyOrNull("NAIS_CLUSTER_NAME") == "dev-gcp"

    fun isLocal(): Boolean = getPropertyOrNull("NAIS_CLUSTER_NAME") == null
}
