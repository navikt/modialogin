package no.nav.modialogin.features.bffproxyfeature.directives

import io.ktor.server.auth.*
import io.prometheus.client.Histogram
import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.cache.CaffeineTokenCache
import no.nav.common.token_client.cache.TokenCache
import no.nav.common.token_client.client.OnBehalfOfTokenClient
import no.nav.common.token_client.utils.env.AzureAdEnvironmentVariables.*
import no.nav.modialogin.common.KotlinUtils.getProperty
import no.nav.modialogin.common.KotlinUtils.requireProperty
import no.nav.modialogin.common.Templating
import no.nav.modialogin.features.authfeature.TokenPrincipal
import no.nav.modialogin.features.bffproxyfeature.BFFProxy
import no.nav.modialogin.features.bffproxyfeature.RedisTokenCache
import no.nav.modialogin.features.bffproxyfeature.RequestDirectiveHandler
import java.util.concurrent.Callable

object AADOnBehalfOfDirectiveSpecification : BFFProxy.RequestDirectiveSpecification {
    /**
     * Usage: SET_ON_BEHALF_OF_TOKEN <cluster> <namespace> <servicename>
     * Ex:
     *  - SET_ON_BEHALF_OF_TOKEN prod-fss pdl pdl-api
     */
    private val regexp = Regex("SET_ON_BEHALF_OF_TOKEN (.*?) (.*?) (.*?)")
    private lateinit var aadOboTokenClient: OnBehalfOfTokenClient
    private val oboExchangeTimer = Histogram.build(
        "azure_ad_obo_exchange_latency_histogram",
        "Distribution of response times when exchanging tokens with Azure AD"
    ).register()

    private data class Lexed(val cluster: String, val namespace: String, val serviceName: String) {
        val scope: String = "api://$cluster.$namespace.$serviceName/.default"
    }

    override fun initialize() {
        aadOboTokenClient = AzureAdTokenClientBuilder.builder()
            // Reimplement `withNaisDefaults` to support reading system properties
            .withClientId(requireProperty(AZURE_APP_CLIENT_ID))
            .withPrivateJwk(requireProperty(AZURE_APP_JWK))
            .withTokenEndpointUrl(requireProperty(AZURE_OPENID_CONFIG_TOKEN_ENDPOINT))
            .withCache(wrapWithRedisCacheIfPresent(CaffeineTokenCache()))
            .buildOnBehalfOfTokenClient()
    }

    override fun canHandle(directive: String): Boolean {
        return regexp.matches(directive)
    }

    override fun createHandler(directive: String): RequestDirectiveHandler {
        return { call ->
            val lexed = lex(Templating.replaceVariableReferences(directive, call))
            val principal = requireNotNull(call.principal<TokenPrincipal>()) {
                "Cannot proxy call with OBO-flow without principals"
            }

            val oboToken: String = oboExchangeTimer.time(Callable {
                aadOboTokenClient.exchangeOnBehalfOfToken(lexed.scope, principal.accessToken.token)
            })

            this.headers["Cookie"] = ""
            this.headers["Authorization"] = "Bearer $oboToken"
        }
    }

    override fun describe(directive: String, sb: StringBuilder) {
        val lexed = lex(directive)
        sb.appendLine("Clearing cookies, and setting authorization header with OBO-token for scope '${lexed.scope}'")
    }

    private fun lex(directive: String): Lexed {
        val match = requireNotNull(regexp.matchEntire(directive))
        val group = match.groupValues.drop(1)
        return Lexed(group[0], group[1], group[2])
    }

    private fun wrapWithRedisCacheIfPresent(cache: TokenCache): TokenCache {
        val url: String = getProperty("REDIS_HOST") ?: return cache
        val password: String = getProperty("REDIS_PASSWORD") ?: return cache

        return RedisTokenCache(
            underlying = cache,
            config = RedisTokenCache.Config(
                host = url,
                password = password,
            )
        )
    }
}
