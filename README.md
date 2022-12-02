# Modialogin mono-repo

Dette repoet inneholder to artifakter:
- frontend-app


Frontend-app er ett tilhørende docker-image som håndterer innlogging av bruker. [Se dokumentasjon](frontend-app/README.md)


## Kjøre lokal
1. Start tredjeparts tjenester ved å kjøre `make start-idea`
2. Start `LocalOidcStub.kt`
3. Start `LocalFrontendApp.kt`
4. Gå til http://localhost:8083/frontend

Alternativt kan man starte alt vha docker-compose ved å kjøre `make build start-silent`, og `make stop` for å stoppe alle tjenestene.

## Henvendelser
Spørsmål knyttet til koden eller prosjektet kan rettes mot:

[Team Personoversikt](https://github.com/navikt/info-team-personoversikt)
