package no.nav.helsearbeidsgiver.utils

import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import no.nav.hag.utils.bakgrunnsjobb.LeaderConfig
import no.nav.helsearbeidsgiver.Env.getPropertyOrNull
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.net.InetAddress

object NaisLeaderConfig : LeaderConfig {
    private const val UNKNOWN_LEADER = "UNKNOWN_LEADER"
    private val httpClient = createHttpClient()

    override fun isElectedLeader(): Boolean = getElectedLeaderId() == getHostName()

    private fun getElectedLeaderId(): String =
        runBlocking {
            val electorUrl = getPropertyOrNull("ELECTOR_GET_URL")
            if (electorUrl != null) {
                try {
                    val electedPod: ElectedPod = httpClient.get(electorUrl).body()
                    logger().debug("Elected leader: ${electedPod.name} and host: ${getHostName()}")
                    electedPod.name
                } catch (e: Exception) {
                    logger().error("Feilet å hente elected leader")
                    sikkerLogger().error("Feilet å hente elected leader", e)
                    UNKNOWN_LEADER
                }
            } else {
                logger().error("ELECTOR_GET_URL er null - er leaderElection enablet i nais?")
                UNKNOWN_LEADER
            }
        }

    private fun getHostName(): String? = InetAddress.getLocalHost().hostName

    @Serializable
    private data class ElectedPod(
        val name: String,
    )
}
