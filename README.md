# Modialogin

Modialogin er en standalone app som implementerer delegert autentisering for sluttbrukere.
Autentiseringen skjer via en ekstern identify provider (idp), som kan konfigureres via `IDP_DISCOVERY_URL` verdien.

## Konfigurasjon
| Navn | Påkrevd | Beskrivelse |
|------|---------|-------------|
| IDP_DISCOVERY_URL | Ja | Url til discovery-url for idp (typisk noe som slutter på .well-known/openid-configuration) |
| IDP_CLIENT_ID | Ja | Systembrukernavn for autentisering mot idp |
| IDP_CLIENT_SECRET | Ja | Systempassord for autentisering mot idp |
| HOST_STATIC_FILES | Nei | Styrer hvorvidt appen også skal host en frontend. Default: `false`. Om satt til `true` mountes `/app` som root for frontend appen. |


## Kjøre lokal
`MainTest.kt` kjøres opp med mock-konfigurasjon, og krever at `OidcStub.kt` er startet og kjører på `localhost:8081`.
Om man ønsker å overstyre noen av mock-verdiene, kan dette gjøres vha `System.setProperty`.

## Henvendelser
For spørsmål og tilbakemeldinger bruk github-issues.

Interne henvendelser kan sendes via Slack i kanalen #team-personoversikt.