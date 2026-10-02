package no.nav.helsearbeidsgiver

object Env {
    fun getPropertyOrNull(prop: String): String? = System.getenv(prop)

    fun isDev(): Boolean = getPropertyOrNull("NAIS_CLUSTER_NAME") == "dev-gcp"
}
