package no.nav.helsearbeidsgiver.utils

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import no.nav.helsearbeidsgiver.Env.getPropertyOrNull
import no.nav.helsearbeidsgiver.Env.isLocal
import no.nav.helsearbeidsgiver.utils.pipe.orDefault

class UnleashFeatureToggles {
    private val unleashClient: Unleash =
        if (isLocal()) {
            FakeUnleash().apply { enableAll() }
        } else {
            val apiUrl =
                getPropertyOrNull("UNLEASH_SERVER_API_URL")
                    .orDefault { throw RuntimeException("UNLEASH_SERVER_API_URL ikke satt") }
            val apiToken =
                getPropertyOrNull("UNLEASH_SERVER_API_TOKEN")
                    .orDefault { throw RuntimeException("UNLEASH_SERVER_API_TOKEN ikke satt") }
            val apiEnv =
                getPropertyOrNull("UNLEASH_SERVER_API_ENV")
                    .orDefault { throw RuntimeException("UNLEASH_SERVER_API_ENV ikke satt") }

            DefaultUnleash(
                UnleashConfig
                    .builder()
                    .appName("refusjon")
                    .instanceId("refusjon")
                    .unleashAPI("$apiUrl/api")
                    .apiKey(apiToken)
                    .environment(apiEnv)
                    .fetchTogglesInterval(15)
                    .build(),
            )
        }
}
