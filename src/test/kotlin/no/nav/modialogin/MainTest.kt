package no.nav.modialogin

fun main() {
    System.setProperty("HOST_STATIC_FILES", "true")
    System.setProperty("IDP_DISCOVERY_URL", "http://localhost:8081/.well-known/openid-configuration")
    System.setProperty("IDP_CLIENT_ID", "modia")
    System.setProperty("IDP_CLIENT_SECRET", "secret here")
    startApplication()
}
