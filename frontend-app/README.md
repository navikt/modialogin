# Frontend-app

Docker-image som sikrer ressursene sine, og bruker en tilhørende `login-app` for innlogging av bruker.

## Konfigurasjon

| Navn              | Påkrevd | Beskrivelse                                                                                                                                                                           |
|-------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| APP_NAME          | Ja      | Navn på applikasjonen. Dette vil bli brukt som context-path i appen.                                                                                                                  |
| APP_VERSION       | Ja      | Version av applikasjonen. Er bare synlig på selftest-siden                                                                                                                            |
| REDIS_HOST        | Ja      | Host til redis-instans                                                                                                                                                                |
| REDIS_PASSWORD    | Ja      | Passord til redis-instans                                                                                                                                                             |
| CDN_BUCKET_URL    | Nei     | Url til CDN-løsning                                                                                                                                       |
| CSP_DIRECTIVES    | Nei     | CSP-header som skal brukes, default: `default-src: 'self'`                                                                                                                            | 
| CSP_REPORT_ONLY   | Nei     | `true` eller `false`, styrer hvorvidt CSP skal være i `Report-Only` modus, default: `false`                                                                                           |
| REFERRER_POLICY   | Nei     | Forhindrer at url-path blir sendt som http header ved lenke klikk. [Les mer her](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Referrer-Policy#examples), Default `origin` |
| PROXY_CONFIG_FILE | Nei     | Plassering av konfigurasjons-filen for proxy-oppsett. Default `/proxy-config.json`                                                                                                    |
| UNLEASH_API_URL   | Nei     | Url til unleash for om man ønsker å bruke unleash-evaluering i templates                                                                                                              |

**NB** For fungerende innlogging mot AzureAD kreves det at følgende properties er satt:
```AZURE_APP_CLIENT_ID, AZURE_APP_CLIENT_SECRET, AZURE_APP_TENANT_ID, AZURE_APP_JWK, AZURE_APP_PRE_AUTHORIZED_APPS, AZURE_APP_WELL_KNOWN_URL, AZURE_OPENID_CONFIG_ISSUER, AZURE_OPENID_CONFIG_JWKS_URI, AZURE_OPENID_CONFIG_TOKEN_ENDPOINT```
Disse blir satt av NAIS-plattformen om man har konfigurert applikasjonen til å bruke
AzureAD, [se dokumentasjon](https://doc.nais.io/security/auth/azure-ad/).

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
  I tilfeller man bruker `RESPOND` så vil ikke `url` feltet fra konfigurationen være i bruk, og man trenger ikke
  spesifisere dette.


## Redis

Imaget krever at man har tilgang på en redis instans for caching av sessionId og obo-tokens. 
For oppsett av dette se på [nais sin dokumentasjon](https://doc.nais.io/persistence/redis/).