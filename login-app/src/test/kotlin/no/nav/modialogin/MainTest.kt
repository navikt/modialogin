package no.nav.modialogin

fun main() {
    System.setProperty("APPNAME", "modialogin")
    System.setProperty("IDP_DISCOVERY_URL", "http://localhost:8080/.well-known/openid-configuration")
    System.setProperty("IDP_CLIENT_ID", "modia-p")
    System.setProperty("IDP_CLIENT_SECRET", "secret")
    System.setProperty("X_FORWARDING_PORT", "8081")
    startApplication()
}
