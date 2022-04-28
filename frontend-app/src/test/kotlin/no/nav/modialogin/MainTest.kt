package no.nav.modialogin

fun main() {
    System.setProperty("APP_NAME", "frontend")
    System.setProperty("APP_VERSION", "localhost")
    System.setProperty("IDP_DISCOVERY_URL", "http://localhost:8080/.well-known/openid-configuration")
    System.setProperty("IDP_CLIENT_ID", "foo")
    System.setProperty("DELEGATED_LOGIN_URL", "http://localhost:8082/modialogin/api/start")
    System.setProperty("AUTH_TOKEN_RESOLVER", "modia_ID_token")
    System.setProperty("CSP_REPORT_ONLY", "true")
    System.setProperty("CSP_DIRECTIVES", "default-src 'self'; script-src 'self';")
    System.setProperty("REFERRER_POLICY", "no-referrer")
    System.setProperty("EXPOSED_PORT", "8083")
    startApplication()
}
