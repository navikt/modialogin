package no.nav.modialogin

fun main() {
    System.setProperty("APP_NAME", "modialogin")
    System.setProperty("APP_VERSION", "localhost")
    System.setProperty("IDP_DISCOVERY_URL", "http://localhost:8080/.well-known/openid-configuration")
    System.setProperty("IDP_CLIENT_ID", "foo")
    System.setProperty("IDP_CLIENT_SECRET", "bar")
    System.setProperty("EXPOSED_PORT", "8082")
    startApplication()
}
