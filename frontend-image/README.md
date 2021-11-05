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
| CSP_DIRECTIVES | Nei | CSP-header som skal brukes, er default satt til `default-src: 'self'` | 
| CSP_REPORT_ONLY | Nei | `true` eller `false`, styrer hvorvidt CSP skal være i `Report-Only` modus |
| REFERRER_POLICY | Nei | Default `origin`. Forhindrer at url-path blir sendt som http header ved lenke klikk. [Les mer her](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Referrer-Policy#examples) |

**NB** Om `AUTH_TOKEN_RESOLVER` settes til `header` vil applikasjonen forvente at access_token kommer via
http-headeren `Authorization: Bearer <token>`.

## Proxying

Appen støtter oppsett av proxyer ved å legge til egne nginx-filer i `/nginx` mappen.
For eksempel;
```nginx
# file: proxy.nginx
# /frontend er her ett eksempel, men bør være lik `APP_NAME`.
# NB: trailing-slashes har her en betydning, fjernes den fra `proxy_pass` så vil hele pathen bli videreført
location  /frontend/proxy/open-endpoint/ {
    proxy_pass http://echo-server/;
}
location  /frontend/proxy/authenticated-endpoint/ {
    access_by_lua_file oidc_protected.lua;
    proxy_pass http://echo-server/;
}

# file Dockerfile
# Vi legger til oppsettet, og det plukkes deretter opp av nginx under oppstart.
COPY proxy.nginx /nginx
```

Man har i stor grad mulighet til å styre hva som blir sendt videre.
F.eks;
- Fjerne cookies slik at tjenesten ikke får deres `ID_token`: `proxy_set_header Cookie "";`
- Rename cookie før proxy; `proxy_set_header Cookie "deres_custom_cookie_navn=$cookie_ID_token;";`
- Legge til ny cookie: `proxy_set_header Cookie "helt_ny_cookie=ny_verdiher; $http_cookie";`
