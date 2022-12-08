package no.nav.modialogin.features.bffproxyfeature.directives

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.prometheus.client.Histogram
import kotlinx.coroutines.runBlocking
import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.cache.CaffeineTokenCache
import no.nav.common.token_client.client.OnBehalfOfTokenClient
import no.nav.modialogin.AzureAdConfig
import no.nav.modialogin.utils.Templating
import no.nav.modialogin.features.authfeature.TokenPrincipal
import no.nav.modialogin.features.bffproxyfeature.BFFProxy
import no.nav.modialogin.features.bffproxyfeature.RedisTokenCache
import no.nav.modialogin.features.bffproxyfeature.RequestDirectiveHandler
import no.nav.modialogin.persistence.RedisPersistence
import java.util.concurrent.Callable

class AADOnBehalfOfDirectiveSpecification(
    azureAdConfig: AzureAdConfig,
    persistence: RedisPersistence<String, String>
) : BFFProxy.RequestDirectiveSpecification {
    /**
     * Usage: SET_ON_BEHALF_OF_TOKEN <cluster> <namespace> <servicename>
     * Ex:
     *  - SET_ON_BEHALF_OF_TOKEN prod-fss pdl pdl-api
     */
    private val regexp = Regex("SET_ON_BEHALF_OF_TOKEN (.*?) (.*?) (.*?)")
    private val aadOboTokenClient: OnBehalfOfTokenClient = AzureAdTokenClientBuilder.builder()
        // Reimplement `withNaisDefaults` to support reading system properties
        .withClientId(azureAdConfig.clientId)
        .withPrivateJwk(azureAdConfig.appJWK)
        .withTokenEndpointUrl(azureAdConfig.openidConfigTokenEndpoint)
        .withCache(RedisTokenCache(CaffeineTokenCache(), persistence))
        .buildOnBehalfOfTokenClient()
    private val oboExchangeTimer = Histogram.build(
        "azure_ad_obo_exchange_latency_histogram",
        "Distribution of response times when exchanging tokens with Azure AD"
    ).register()

    private data class Lexed(val cluster: String, val namespace: String, val serviceName: String) {
        val scope: String = "api://$cluster.$namespace.$serviceName/.default"
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

            try {
                val oboToken: String = oboExchangeTimer.time(Callable {
                    aadOboTokenClient.exchangeOnBehalfOfToken(lexed.scope, principal.accessToken.token)
                })

                this.headers["Cookie"] = ""
                this.headers["Authorization"] = "Bearer $oboToken"
            } catch (e: Throwable) {
                runBlocking {
                    call.respond(status = HttpStatusCode.InternalServerError, "AADOnBehalfOfDirectiveSpecification failed: ${e.message ?: e.localizedMessage}")
                }
            }
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
}
