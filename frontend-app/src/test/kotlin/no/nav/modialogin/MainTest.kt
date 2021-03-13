package no.nav.modialogin

fun main() {
    System.setProperty("APPNAME", "modiafrontend")
    System.setProperty("IDP_DISCOVERY_URL", "http://localhost:8080/.well-known/openid-configuration")
    System.setProperty("IDP_CLIENT_ID", "modia-p")
    System.setProperty("DELEGATED_LOGIN_URL", "http://localhost:8081/modialogin/api/start")
    System.setProperty("X_FORWARDING_PORT", "8082")
    startApplication()
}
