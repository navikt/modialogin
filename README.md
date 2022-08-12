# Modialogin mono-repo

Dette repoet inneholder to artifakter:
- login-app
- frontend-image

Login-app deployes automatisk som modialogin, men kan også deployes under ett annet navn om ønskelig. [Se dokumentasjon](login-app/README.md)

Frontend-image er ett tilhørende docker-image som brukes login-app for innlogging av bruker. [Se dokumentasjon](frontend-image/README.md)


## Kjøre lokal
`MainTest.kt` kjøres opp med mock-konfigurasjon, og krever at `OidcStub.kt` er startet og kjører på `localhost:8081`.
Om man ønsker å overstyre noen av mock-verdiene, kan dette gjøres vha `System.setProperty`.

## Henvendelser
Spørsmål knyttet til koden eller prosjektet kan rettes mot:

[Team Personoversikt](https://github.com/navikt/info-team-personoversikt)
