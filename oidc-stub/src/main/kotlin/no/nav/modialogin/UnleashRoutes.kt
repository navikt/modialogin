package no.nav.modialogin

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
class FeaturesResponse(
    val version: Int = 1,
    val features: List<Feature>
)
@Serializable
data class Feature(
    val name: String,
    val enabled: Boolean,
    val stale: Boolean = false,
    val strategies: List<Map<String, String>> = listOf(
        mapOf(
            "name" to "default"
        )
    ),
    val type: String = "mock",
    val variants: List<String> = emptyList()
)

fun Application.unleash(vararg features: Pair<String, Boolean>) {
    val features = FeaturesResponse(
        features = features.map { entry -> Feature(name = entry.first, enabled = entry.second) }
    )

    routing {
        route("unleash/api") {
            get("client/features") {
                call.respond(features)
            }
        }
    }
}