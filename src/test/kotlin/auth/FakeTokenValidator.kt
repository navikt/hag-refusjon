package no.nav.helsearbeidsgiver.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

const val GYLDIG_TOKEN = "gyldig-token"

class FakeTokenValidator : TokenValidator {
    override suspend fun valider(token: String): JsonObject? =
        if (token == GYLDIG_TOKEN) {
            JsonObject(mapOf("active" to JsonPrimitive(true)))
        } else {
            null
        }
}
