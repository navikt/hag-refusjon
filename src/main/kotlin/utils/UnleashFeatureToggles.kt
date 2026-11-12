package no.nav.helsearbeidsgiver.utils

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import no.nav.helsearbeidsgiver.Env.getProperty
import no.nav.helsearbeidsgiver.Env.isLocal

class UnleashFeatureToggles {
    private val unleashClient: Unleash =
        if (isLocal()) {
            FakeUnleash().apply { enableAll() }
        } else {
            DefaultUnleash(
                UnleashConfig
                    .builder()
                    .appName("refusjon")
                    .instanceId("refusjon")
                    .unleashAPI(getProperty("UNLEASH_SERVER_API_URL") + "/api")
                    .apiKey(getProperty("UNLEASH_SERVER_API_TOKEN"))
                    .environment(getProperty("UNLEASH_SERVER_API_ENV"))
                    .fetchTogglesInterval(15)
                    .build(),
            )
        }
}
