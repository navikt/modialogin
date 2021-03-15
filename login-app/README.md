# Login-app

login-app en standalone app som implementerer delegert autentisering for sluttbrukere.
Autentiseringen skjer via en ekstern identify provider (idp), som kan konfigureres via `IDP_DISCOVERY_URL` verdien.

## Konfigurasjon
| Navn | Påkrevd | Beskrivelse | Default |
|------|---------|-------------|---------|
| APP_NAME | Ja | Navn på applikasjonen. Dette vil bli brukt som context-path i appen. | |
| APP_VERSION | Ja | Version av applikasjonen. Er bare synlig på selftest-siden | |
| IDP_DISCOVERY_URL | Ja | Url til discovery-url for idp (typisk noe som slutter på .well-known/openid-configuration) | |
| IDP_CLIENT_ID | Ja | Systembrukernavn for autentisering mot idp | |
| IDP_CLIENT_SECRET | Ja | Systempassord for autentisering mot idp | |
| AUTH_TOKEN_RESOLVER | Nei | Hvor appen kan forvente å finne ID_token. F.eks `ID_token` | `ID_token` |
| REFRESH_TOKEN_RESOLVER | Nei | Hvor appen kan forvente å finne ID_token. F.eks `refresh_token` | `refresh_token` |