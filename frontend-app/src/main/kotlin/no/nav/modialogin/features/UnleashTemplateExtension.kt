package no.nav.modialogin.features

import com.auth0.jwt.JWT
import io.getunleash.UnleashContext
import io.ktor.server.application.*
import io.ktor.server.auth.*
import no.nav.modialogin.common.TemplatingEngine
import no.nav.modialogin.common.unleash.UnleashService
import no.nav.modialogin.features.authfeature.AuthFilterPrincipals

object UnleashTemplateExtension {
    class Context(
        val call: ApplicationCall,
        val unleash: UnleashService
    )

    val Source = TemplatingEngine.Source<Context>(
        prefix = "unleash",
        replacement = { ctx, name ->
            val userId = ctx.call.principal<AuthFilterPrincipals>()
                ?.principals
                ?.firstOrNull()
                ?.token
                ?.let { JWT.decode(it) }
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

            ctx.unleash.isEnabled(name, unleashCtx).toString()
        }
    )
}