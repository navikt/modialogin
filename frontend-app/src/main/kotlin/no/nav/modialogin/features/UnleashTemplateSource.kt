package no.nav.modialogin.features

import io.getunleash.Unleash
import io.getunleash.UnleashContext
import io.ktor.server.application.*
import io.ktor.server.auth.*
import no.nav.modialogin.common.TemplatingEngine
import no.nav.modialogin.features.authfeature.TokenPrincipal

object UnleashTemplateSource {
    fun create(unleash: Unleash): TemplatingEngine.Source<ApplicationCall?> {
        return TemplatingEngine.Source(
            prefix = "unleash",
            replacement = { call, name ->
                val userId = call
                    ?.principal<TokenPrincipal>()
                    ?.accessToken
                    ?.let {
                        it.getClaim("NAVident").asString() ?: it.subject
                    }
                val unleashCtx = UnleashContext
                    .builder()
                    .apply {
                        if (userId != null) {
                            userId(userId)
                        }
                    }
                    .build()

                unleash.isEnabled(name, unleashCtx).toString()
            }
        )
    }
}