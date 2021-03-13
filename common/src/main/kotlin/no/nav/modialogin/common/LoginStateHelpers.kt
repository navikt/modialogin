package no.nav.modialogin.common

import io.ktor.http.*
import io.ktor.request.*
import java.math.BigInteger
import kotlin.random.Random

object LoginStateHelpers {
    fun generateStateNounce(): String {
        val bytes = Random.nextBytes(20)
        return "state_${BigInteger(1, bytes).toString(16)}"
    }

    fun callbackUrl(authorizationEndpoint: String, clientId: String, stateNounce: String, callbackUrl: String): String {
        val requestParameters = Parameters.build {
            set("client_id", clientId)
            set("state", stateNounce)
            set("redirect_uri", callbackUrl)
        }

        val parameters = kerberosTriggerParameters + openidCodeFlowParameters + requestParameters
        return "$authorizationEndpoint?${parameters.formUrlEncode()}"
    }

    fun loginUrl(request: ApplicationRequest, xForwardingPort: Int, appname: String): String {
        val port = if (xForwardingPort == 8080) "" else ":$xForwardingPort"
        val scheme = if (xForwardingPort == 8080) "https" else "http"
        return "$scheme://${request.host()}$port/$appname/api/login"
    }

    private val kerberosTriggerParameters = Parameters.build {
        set("session", "winssochain")
        set("authIndexType", "service")
        set("authIndexValue", "winssochain")
    }
    private val openidCodeFlowParameters = Parameters.build {
        set("response_type", "code")
        set("scope", "openid")
    }
}
