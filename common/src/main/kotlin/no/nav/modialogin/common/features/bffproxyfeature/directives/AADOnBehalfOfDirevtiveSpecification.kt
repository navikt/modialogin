package no.nav.modialogin.common.features.bffproxyfeature.directives

import io.ktor.server.auth.*
import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.client.OnBehalfOfTokenClient
import no.nav.common.token_client.utils.env.AzureAdEnvironmentVariables.*
import no.nav.modialogin.common.KotlinUtils.getEnvProperty
import no.nav.modialogin.common.Templating
import no.nav.modialogin.common.features.authfeature.AuthFeature
import no.nav.modialogin.common.features.bffproxyfeature.BFFProxy
import no.nav.modialogin.common.features.bffproxyfeature.RequestDirectiveHandler

object AADOnBehalfOfDirevtiveSpecification : BFFProxy.RequestDirectiveSpecification {
    /**
     * Usage: SET_ON_BEHALF_OF_TOKEN <cluster> <namespace> <servicename>
     * Ex:
     *  - SET_ON_BEHALF_OF_TOKEN prod-fss pdl pdl-api
     */
    private val regexp = Regex("SET_ON_BEHALF_OF_TOKEN (.*?) (.*?) (.*?)")

    private data class Lexed(val cluster: String, val namespace: String, val serviceName: String) {
        val scope: String = "api://$cluster.$namespace.$serviceName/.default"
    }

    val aadOboTokenClient: OnBehalfOfTokenClient = AzureAdTokenClientBuilder.builder()
        // Reimplement `withNaisDefaults` to support reading system properties
        .withClientId(getEnvProperty(AZURE_APP_CLIENT_ID))
        .withPrivateJwk(getEnvProperty(AZURE_APP_JWK))
        .withTokenEndpointUrl(getEnvProperty(AZURE_OPENID_CONFIG_TOKEN_ENDPOINT))
        .buildOnBehalfOfTokenClient()

    override fun canHandle(directive: String): Boolean {
        return regexp.matches(directive)
    }

    override fun createHandler(directive: String): RequestDirectiveHandler {
        return { call ->
            val lexed = lex(Templating.replaceVariableReferences(directive, call.request))
            val principal = requireNotNull(call.principal<AuthFeature.PayloadPrincipal>()) {
                "Cannot proxy call with OBO-flow without principal"
            }
            val token = principal.token
            val oboToken = aadOboTokenClient.exchangeOnBehalfOfToken(lexed.scope, token)

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
}
