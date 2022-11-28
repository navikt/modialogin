const { test, testonly, assertThat, verify, setup, retry, equals, isDefined, isNotDefined, startsWith, contains, notContains, hasLengthGreaterThen } = require('./test-lib');
const { fetch, fetchJson, fetchText } = require('./http-fetch');

setup('oidc-stub is running', retry({ retry: 10, interval: 2}, async () => {
    const oidcConfig = await fetchJson('http://localhost:8080/azuread/.well-known/openid-configuration');
    verify(oidcConfig.statusCode, 200, 'oidcConfig is running');
}));

setup('cdnstub is running', retry({ retry: 10, interval: 2}, async () => {
    const loginapp = await fetch('http://localhost:8091/cdn/frontend/index.html');
    verify(loginapp.statusCode, 200, 'cdnstub is running');
}));

setup('frontendapp is running', retry({ retry: 10, interval: 2}, async () => {
    const frontendapp = await fetch('http://localhost:8083/frontend/internal/isAlive');
    verify(frontendapp.statusCode, 200, 'frontendapp is running');
}));

test('oidc-stub provides jwks', async () => {
    const jwks = await fetchJson('http://localhost:8080/azuread/.well-known/jwks.json');
    assertThat(jwks.body, isDefined, 'jwks.json returns a json body');
    assertThat(jwks.body.keys.length, 1, 'jwks has one key');
});

test('attempts to get frontend resource should result in login-flow', async () => {
    const sessionId = await azureadloginflow("8083");
    const pageLoadAfterLogin = await fetch(`http://localhost:8083/frontend/`, {
        'Cookie': sessionId
    });

    assertThat(pageLoadAfterLogin.statusCode, 200, '/frontend returns 200');
    assertThat(pageLoadAfterLogin.body, contains('<!DOCTYPE html>'), '/frontend returns HTML')
});

test('static resources returns 302 login redirect, if not logged in', async () => {
    const staticResource = await fetch('http://localhost:8083/frontend/static/css/index.css');
    assertThat(staticResource.statusCode, 302, '/frontend returns 302');
    assertThat(staticResource.body, notContains('<!DOCTYPE html>'), 'css-file is not HTML')
});

test('static resources returns 200 ok if logged in', async () => {
    const tokens = await fetchJson('http://localhost:8080/azuread/oauth/token', {}, {});
    const sessionId = await createSessionId(tokens);
    const staticResource = await fetch('http://localhost:8083/frontend/static/css/index.css', {
        'Cookie': `frontend-session=${sessionId};`
    });
    assertThat(staticResource.statusCode, 200, '/frontend returns 200');
    assertThat(staticResource.body, notContains('<!DOCTYPE html>'), 'css-file is not HTML')
});

test('frontend routing should return index.html', async () => {
    const tokens = await fetchJson('http://localhost:8080/azuread/oauth/token', {}, {});
    const sessionId = await createSessionId(tokens);
    const staticResource = await fetch('http://localhost:8083/frontend/some/spa-route?query=param', {
        'Cookie': `frontend-session=${sessionId};`
    });
    assertThat(staticResource.statusCode, 200, '/frontend returns 200');
    assertThat(staticResource.body, contains('<!DOCTYPE html>'), 'css-file is not HTML')
});

test('frontend routing should return 302 if not logged in', async () => {
    const staticResource = await fetch('http://localhost:8083/frontend/some/spa-route/');
    assertThat(staticResource.statusCode, 302, '/frontend/some/spa-route/ returns 302');
    assertThat(staticResource.body, notContains('<!DOCTYPE html>'), 'css-file is not HTML')
});

test('missing static resource returns 404 instead of fallback to index.html', async () => {
    const tokens = await fetchJson('http://localhost:8080/azuread/oauth/token', {}, {});
    const sessionId = await createSessionId(tokens);
    const staticResource = await fetch('http://localhost:8083/frontend/static/css/missing.css',{
        'Cookie': `frontend-session=${sessionId};`
    });
    assertThat(staticResource.statusCode, 404, '/frontend returns 404');
    assertThat(staticResource.body, notContains('<!DOCTYPE html>'), 'css-file is not HTML')
});

test('proxying to open endpoint when logged in', async () => {
    const tokens = await fetchJson('http://localhost:8080/azuread/oauth/token', {}, {});
    const sessionId = await createSessionId(tokens);
    const openEndpointWithCookie = await fetchJson('http://localhost:8083/frontend/proxy/open-endpoint/data', {
        'Cookie': `frontend-session=${sessionId};`
    });
    assertThat(openEndpointWithCookie.statusCode, 200, '/frontend proxied to open endpoint');
    assertThat(openEndpointWithCookie.body.path, '/data', '/frontend removed url prefix');
    assertThat(openEndpointWithCookie.body.headers['cookie'], isDefined, '/frontend did send cookie');
});

test('proxying to open endpoint that removes cookie when logged in', async () => {
    const tokens = await fetchJson('http://localhost:8080/azuread/oauth/token', {}, {});
    const sessionId = await createSessionId(tokens);
    const openEndpointWithCookie = await fetchJson('http://localhost:8083/frontend/proxy/open-endpoint-no-cookie/data', {
        'Cookie': `frontend-session=${sessionId};`
    });
    assertThat(openEndpointWithCookie.statusCode, 200, '/frontend proxied to open endpoint');
    assertThat(openEndpointWithCookie.body.path, '/data', '/frontend removed url prefix');
    assertThat(openEndpointWithCookie.body.headers['cookie'], isNotDefined, '/frontend did not send cookie');
});

test('proxying to protected endpoint when not logged in', async () => {
    const protectedEndpoint = await fetch('http://localhost:8083/frontend/proxy/protected-endpoint/data');
    assertThat(protectedEndpoint.statusCode, 302, '/frontend returns 302');
    assertThat(
        protectedEndpoint.redirectURI.path,
        '/frontend/oauth2/login',
        '/frontend redirects to login'
    );
});

test('proxying to protected endpoint when logged in', async () => {
    const tokens = await fetchJson('http://localhost:8080/azuread/oauth/token', {}, {});
    const sessionId = await createSessionId(tokens);
    const protectedEndpoint = await fetchJson('http://localhost:8083/frontend/proxy/protected-endpoint/data', {
        'Cookie': `frontend-session=${sessionId};`
    });
    assertThat(protectedEndpoint.statusCode, 200, '/frontend returns 200');
    assertThat(protectedEndpoint.body.path, '/data', '/frontend removed url prefix');
    assertThat(protectedEndpoint.body.headers['cookie'], isDefined, '/frontend did send cookie');
    assertThat(protectedEndpoint.body.headers['cookie'], startsWith('frontend-session'), '/frontend sent frontend-session cookie');
});

test('proxying to protected endpoint when logged in, and rewriting cookie name', async () => {
    const tokens = await fetchJson('http://localhost:8080/azuread/oauth/token', {}, {});
    const sessionId = await createSessionId(tokens);
    const protectedEndpoint = await fetchJson('http://localhost:8083/frontend/proxy/protected-endpoint-with-cookie-rewrite/data', {
        'Cookie': `frontend-session=${sessionId};`
    });
    assertThat(protectedEndpoint.statusCode, 200, '/frontend returns 200');
    assertThat(protectedEndpoint.body.path, '/data', '/frontend removed url prefix');
    assertThat(protectedEndpoint.body.headers['cookie'], startsWith('ID_token'), '/frontend sent ID_token cookie');
    assertThat(protectedEndpoint.body.headers['cookie'], notContains('frontend-session'), '/frontend did not send frontend-session cookie');
});

test('proxying with obo-flow-directive exchanges the provided token', async () => {
    const tokens = await fetchJson('http://localhost:8080/azuread/oauth/token', {}, {});
    const sessionId = await createSessionId(tokens);
    const openToken = tokens.body['id_token']
    const apiEndpoint = await fetchJson('http://localhost:8083/frontend/api/some/data/endpoint', {
        'Cookie': `frontend-session=${sessionId};`
    });

    assertThat(apiEndpoint.statusCode, 200, '/api returns 200')
    assertThat(apiEndpoint.body.path, '/modiapersonoversikt-api/some/data/endpoint', 'correct path is used by proxy')
    assertThat(apiEndpoint.body.headers['cookie'], equals(''), 'authorization header is set')
    assertThat(apiEndpoint.body.headers['authorization'], startsWith("Bearer "), 'authorization header is different from token')
    assertThat(apiEndpoint.body.headers['authorization'], notContains(openToken), 'authorization header is different from token')
});

test('should use redis-cache', async () => {
    const sessionCookieValue = await azureadloginflow("8083");
    await fetchJson('http://localhost:8083/frontend/api/some/data/endpoint', {
        'Cookie': sessionCookieValue,
    });
    const redisCachekeys = await fetchJson('http://localhost:8080/redis/keys');
    assertThat(redisCachekeys.body, hasLengthGreaterThen(0), "Redis should have cached some keys")
});

test('environments variables are injected into nginx config', async () => {
    const tokens = await fetchJson('http://localhost:8080/azuread/oauth/token', {}, {});
    const sessionId = await createSessionId(tokens);
    const page = await fetch('http://localhost:8083/frontend/env-data', {
        'Cookie': `frontend-session=${sessionId};`
    });
    assertThat(page.body, 'APP_NAME: frontend', 'Page contains environmentvariable value')
});

test('environments and unleash variables are injected into html config', async () => {
    const tokens = await fetchJson('http://localhost:8080/azuread/oauth/token', {}, {});
    const sessionId = await createSessionId(tokens);
    const page = await fetch('http://localhost:8083/frontend/and/some/path', {
        'Cookie': `frontend-session=${sessionId};`
    });
    assertThat(page.body, contains('&#36;env{APP_NAME}: frontend'), 'Page contains environmentvariable value')
    assertThat(page.body, contains('Feature 1: true'), 'Page contains enabled unleash variable')
    assertThat(page.body, contains('Feature 2: false'), 'Page contains disabled unleash variable')
});

test('csp directive is added to request', async () => {
    const tokens = await fetchJson('http://localhost:8080/azuread/oauth/token', {}, {});
    const sessionId = await createSessionId(tokens);
    const page = await fetch('http://localhost:8083/frontend/', {
        'Cookie': `frontend-session=${sessionId};`
    });

    const cspPolicy = page.headers['content-security-policy-report-only'];
    assertThat(cspPolicy, isDefined, '/frontend has report-only CSP-policy');
    assertThat(cspPolicy, contains('script-src'), '/frontend has report-only CSP-policy');
});

test('referrer-policy is added to response', async () => {
    const tokens = await fetchJson('http://localhost:8080/azuread/oauth/token', {}, {});
    const sessionId = await createSessionId(tokens);
    const page = await fetch('http://localhost:8083/frontend/', {
        'Cookie': `frontend-session=${sessionId};`
    });
    assertThat(page.headers['referrer-policy'], 'no-referrer', '/frontend has referrer-policy');
});

test('personal number is not scraped by Micrometer when proxy is used and the user is not logged in', async () => {
    const personalNumber = '12345678910'

    await fetch(`http://localhost:8083/frontend/proxy/${personalNumber}`)
    const scrapeDump = await fetchText(`http://localhost:8083/frontend/internal/metrics`)
    assertThat(scrapeDump.body, notContains(personalNumber), '/frontend masks personal number')
});

async function azureadloginflow(port) {
    const initial = await fetch(`http://localhost:${port}/frontend/`);

    assertThat(initial.statusCode, 302, '/frontend returns 302');
    assertThat(
        initial.redirectURI.path,
        '/frontend/oauth2/login',
        '/frontend redirects to /frontend/oauth2/login'
    );
    assertThat(
        initial.redirectURI.queryParams.redirect,
        encodeURIComponent(`http://localhost:${port}/frontend/`),
        '/frontend redirect passes original url encoded in queryparameter'
    );

    const startLogin = await fetch('http://localhost:8083'+initial.redirectURI.uri);

    assertThat(initial.statusCode, 302, '/frontend/oauth2/login returns 302');
    assertThat(
        startLogin.redirectURI.path,
        'http://localhost:8080/azuread/authorize',
        '/frontend/oauth2/login redirects to oidc-stub/azuread/authorize'
    );

    const state = startLogin.redirectURI.queryParams.state;
    assertThat(state, isDefined, '/frontend/oauth2/login state query-param is present')
    assertThat(
        startLogin.redirectURI.queryParams,
        {
            client_id: 'foo',
            redirect_uri: encodeURIComponent(`http://localhost:8083/frontend/oauth2/callback?redirect=${encodeURIComponent('http://localhost:8083/frontend/')}`),
            scope: 'openid+offline_access+api%3A%2F%2Ffoo%2F.default',
            state,
            response_type: 'code',
            // response_mode: 'query',
        },
        '/frontend/oauth2/login passes correct queryParams to idp'
    )

    const authorize = await fetch(startLogin.redirectURI.uri);
    assertThat(authorize.statusCode, 302, '/oidc-stub/azuread/authorize returns 302');
    assertThat(
        authorize.redirectURI.path,
        'http://localhost:8083/frontend/oauth2/callback',
        '/oidc-stub/azuread/authorize redirects to frontend/oauth2/callback'
    );
    const code = authorize.redirectURI.queryParams.code;
    assertThat(code, isDefined, '/oidc-stub/authorize code query-param is present');
    assertThat(
        authorize.redirectURI.queryParams.state,
        state,
        '/oidc-stub/azuread/authorize state query-param matches state sent in from modialogin'
    );

    const login = await fetch(authorize.redirectURI.uri, {
        'Cookie': ''
    });
    assertThat(login.statusCode, 302, '/modialogin/api/login returns 302');
    assertThat(
        login.redirectURI.path,
        `http://localhost:${port}/frontend/`,
        '/modialogin/login redirects to /frontend'
    );
    const loginCookies = login.headers['set-cookie'];
    const sessionCookie = loginCookies.find(cookie => cookie.startsWith('frontend-session'));
    assertThat(sessionCookie, isDefined, '/frontend/oauth/callback sets session cookie');
    assertThat(sessionCookie, contains('Path=/frontend'), '/frontend/oauth/callback has path');
    return sessionCookie;
}

async function createSessionId(tokens) {
    const sessionId = Math.random().toString(36).substring(2);
    const data = {
        accessToken: tokens.body['access_token'],
        refreshToken: tokens.body['refresh_token'],
    };
    const resp = await fetch(`http://localhost:8080/redis/session/${sessionId}`, {}, data);
    return sessionId;
}