# Frontend-image

Docker-image som sikrer ressursene sine, og bruker en tilhørende `login-app` for innlogging av bruker.

## Konfigurasjon
| Navn | Påkrevd | Beskrivelse |
|------|---------|-------------|
| APP_NAME | Ja | Navn på applikasjonen. Dette vil bli brukt som context-path i appen. |
| APP_VERSION | Ja | Version av applikasjonen. Er bare synlig på selftest-siden |
| IDP_DISCOVERY_URL | Ja | Url til discovery-url for idp (typisk noe som slutter på .well-known/openid-configuration) |
| IDP_CLIENT_ID | Ja | Systembrukernavn for autentisering mot idp |
| DELEGATED_LOGIN_URL | Ja | Url til `login-app`, e.g `http://domain.nav.no/loginapp/api/start` |
| AUTH_TOKEN_RESOLVER | Ja | Hvor appen kan forvente å finne ID_token. F.eks `ID_token` eller `header` |

**NB** Om `AUTH_TOKEN_RESOLVER` settes til `header` vil applikasjonen forvente at access_token kommer via
http-headeren `Authorization: Bearer <token>`.

