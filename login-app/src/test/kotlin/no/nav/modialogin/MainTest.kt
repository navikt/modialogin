package no.nav.modialogin

fun main() {
    System.setProperty("APP_NAME", "modialogin")
    System.setProperty("APP_VERSION", "localhost")
    System.setProperty("IDP_DISCOVERY_URL", "http://localhost:8080/.well-known/openid-configuration")
    System.setProperty("IDP_CLIENT_ID", "foo")
    System.setProperty("IDP_CLIENT_SECRET", "bar")
    System.setProperty("X_FORWARDING_PORT", "8081")
    startApplication()
}
