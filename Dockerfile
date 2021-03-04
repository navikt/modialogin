FROM docker.pkg.github.com/navikt/pus-nais-java-app/pus-nais-java-app:java11
COPY /build/libs/modialogin-fatjar.jar app.jar