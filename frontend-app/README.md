# Frontend-app

Docker-image som sikrer ressursene sine, og bruker en tilhørende `login-app` for innlogging av bruker.

## Konfigurasjon
| Navn                   | Påkrevd | Beskrivelse                                                                                                                                                                            |
|------------------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| APP_NAME               | Ja      | Navn på applikasjonen. Dette vil bli brukt som context-path i appen.                                                                                                                   |
| APP_VERSION            | Ja      | Version av applikasjonen. Er bare synlig på selftest-siden                                                                                                                             |
| IDP_DISCOVERY_URL      | Nei     | Url til discovery-url for idp (typisk noe som slutter på .well-known/openid-configuration)                                                                                             |
| IDP_CLIENT_ID          | Nei     | Systembrukernavn for autentisering mot idp                                                                                                                                             |
| IDP_ISSUER             | Nei     | Issuer for token, e.g: `https://your.ipd.no:443/oauth2`                                                                                                                                |
| DELEGATED_LOGIN_URL    | Nei     | Url til `login-app`, e.g `http://domain.nav.no/loginapp/api/start`                                                                                                                     |
| DELEGATED_REFRESH_URL  | Nei     | Url til `login-app` for refreshing av token, e.g `http://domain.nav.no/loginapp/api/refresh`                                                                                           |
| AUTH_TOKEN_RESOLVER    | Nei     | Hvor appen kan forvente å finne ID_token. F.eks `ID_token` eller `header`, default: `ID_token`                                                                                         |
| REFRESH_TOKEN_RESOLVER | Nei     | Hvor appen kan forvente å finne refreshtoken. F.eks `refresh_token`, default: `refresh_token`                                                                                          |
| CSP_DIRECTIVES         | Nei     | CSP-header som skal brukes, default: `default-src: 'self'`                                                                                                                             | 
| CSP_REPORT_ONLY        | Nei     | `true` eller `false`, styrer hvorvidt CSP skal være i `Report-Only` modus, default: `false`                                                                                            |
| REFERRER_POLICY        | Nei     | Forhindrer at url-path blir sendt som http header ved lenke klikk. [Les mer her](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Referrer-Policy#examples), Default `origin` |
| PROXY_CONFIG_FILE      | Nei     | Plassering av konfigurasjons-filen for proxy-oppsett. Default `/proxy-config.json`                                                                                                     |
| DISABLE_AZURE_AD       | Nei     | Forhindrer at AzureAd konfigurasjon blir tatt i bruk. Kan brukes når man ønsker å registere en applikasjon i AzureAd uten at tilgangskontrollen slår inn. Default: `false`             |

**NB** For fungerende innlogging mot OpenAM kreves det at følgende properties er satt:
```IDP_DISCOVERY_URL, IDP_CLIENT_ID, IDP_ISSUER, DELEGATED_LOGIN_URL, DELEGATED_REFRESH_URL```
Om alle disse er satt, men man alikevel ønsker at OpenAM innlogging skal være skrudd av kan man sette `DISABLE_OPEN_AM=true`.

**NB** For fungerende innlogging mot AzureAD kreves det at følgende properties er satt:
```AZURE_APP_CLIENT_ID, AZURE_APP_CLIENT_SECRET, AZURE_APP_TENANT_ID, AZURE_APP_JWK, AZURE_APP_PRE_AUTHORIZED_APPS, AZURE_APP_WELL_KNOWN_URL, AZURE_OPENID_CONFIG_ISSUER, AZURE_OPENID_CONFIG_JWKS_URI, AZURE_OPENID_CONFIG_TOKEN_ENDPOINT```
Disse blir satt av NAIS-plattformen om man har konfigurert applikasjonen til å bruke AzureAD, [se dokumentasjon](https://doc.nais.io/security/auth/azure-ad/).
Om alle disse er satt, men man alikevel ønsker at OpenAM innlogging skal være skrudd av kan man sette `DISABLE_AZURE_AD=true`.


**NB** Om `AUTH_TOKEN_RESOLVER` settes til `header` vil applikasjonen forvente at access_token kommer via
http-headeren `Authorization: Bearer <token>`.

## Proxying

Appen støtter oppsett av proxyer ved å legge til filen `/proxy-config.json`.
For eksempel;
```Dockerfile
# I din Dockerfile
ADD proxy-config.json /proxy-config.json
```

Innholdet i filen må være på følgende format:
```json
[
  {
    "prefix": "proxy/api1",
    "url": "http://domain.other/appname/rest",
    "rewriteDirectives": [
      "SET_HEADER Cookie ''",
      "SET_HEADER Authorization '$cookie{loginapp_ID_token}'"
    ]
  }
]
```

Gitt konfigurasjonen ovenfor vil urlen `http://din.app/proxy/api/person/data` blir redirected til
`http://domain.other/appname/rest/person/data`.

Videre har man mulighet til å styre hvordan ett proxy-endepunkt oppfører seg vha `rewriteDirectives`:
F.eks;
- Fjerne cookies slik at tjenesten ikke får deres `SET_HEADER Cookie ''`
- Sette cookie før proxy; `SET_HEADER Cookie 'deres_custom_cookie_navn=$cookie{cookie_ID_token}'`
- Sette annen header før proxy; `SET_HEADER Authorization '$env{token-from-environment}'`
- Hardkode svar; `RESPOND 200 '$env{token-from-environment} $header{Content-Type} $cookie{ID_token}'`
  I tilfeller man bruker `RESPOND` så vil ikke `url` feltet fra konfigurationen være i bruk, og man trenger ikke spesifisere dette.
