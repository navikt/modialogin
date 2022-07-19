package no.nav.modialogin

fun main() {
    System.setProperty("IS_LOCALHOST_DEV", "true")

    System.setProperty("APP_NAME", "modialogin")
    System.setProperty("APP_VERSION", "localhost")
    System.setProperty("IDP_DISCOVERY_URL", "http://localhost:8080/openam/.well-known/openid-configuration")
    System.setProperty("IDP_CLIENT_ID", "foo")
    System.setProperty("IDP_CLIENT_SECRET", "bar")
    System.setProperty("AUTH_TOKEN_RESOLVER", "modia_ID_token")
    System.setProperty("REFRESH_TOKEN_RESOLVER", "modia_refresh_token")
    System.setProperty("EXPOSED_PORT", "8082")
    System.setProperty("OUTSIDE_DOCKER", "true")
    startApplication()
}
