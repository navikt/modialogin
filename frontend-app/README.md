# Frontend-app

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
| CSP_DIRECTIVES | Nei | CSP-header som skal brukes, er default satt til `default-src: 'self'` | 
| CSP_REPORT_ONLY | Nei | `true` eller `false`, styrer hvorvidt CSP skal være i `Report-Only` modus |
| REFERRER_POLICY | Nei | Default `origin`. Forhindrer at url-path blir sendt som http header ved lenke klikk. [Les mer her](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Referrer-Policy#examples) |


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