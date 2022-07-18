const { test, assertThat, verify, setup, retry, equals, isDefined, isNotDefined, startsWith, contains, notContains, hasLengthGreaterThen } = require('./test-lib');
const { fetch, fetchJson } = require('./http-fetch');
const {testonly} = require("./test-lib.js");

setup('oidc-stub is running', retry({ retry: 10, interval: 2}, async () => {
    const oidcConfig = await fetchJson('http://localhost:8080/openam/.well-known/openid-configuration');
    verify(oidcConfig.statusCode, 200, 'oidcConfig is running');
}));

setup('modialogin is running', retry({ retry: 10, interval: 2}, async () => {
    const loginapp = await fetch('http://localhost:8082/modialogin/internal/isAlive');
    verify(loginapp.statusCode, 200, 'loginapp is running');
}));

setup('frontendapp is running', retry({ retry: 10, interval: 2}, async () => {
    const frontendapp = await fetch('http://localhost:8083/frontend/internal/isAlive');
    verify(frontendapp.statusCode, 200, 'frontendapp is running');
}));

test('oidc-stub provides jwks', async () => {
    const jwks = await fetchJson('http://localhost:8080/openam/.well-known/jwks.json');
    assertThat(jwks.body, isDefined, 'jwks.json returns a json body');
    assertThat(jwks.body.keys.length, 1, 'jwks has one key');
});
/**
 * Redirect not required
 */
// test('attempts to get frontend resource without trailing slash', async () => {
//     const initial = await fetch('http://localhost:8083/frontend');
//     assertThat(initial.statusCode, 301, '/frontend returns 301');
//     assertThat(initial.redirectURI.path, 'frontend/', 'appends trailing slash');
// });

async function issologinflow(port) {
    const initial = await fetch(`http://localhost:${port}/frontend/`);

    assertThat(initial.statusCode, 302, '/frontend returns 302');
    assertThat(
        initial.redirectURI.path,
        'http://localhost:8082/modialogin/api/start',
        '/frontend redirects to /modialogin/api/start'
    );
    assertThat(
        initial.redirectURI.queryParams.redirect,
        encodeURIComponent(`http://localhost:${port}/frontend/`),
        '/frontend redirect passes original url encoded in queryparameter'
    );

    const startLogin = await fetch(initial.redirectURI.uri);

    assertThat(initial.statusCode, 302, '/modialogin/api/start returns 302');
    assertThat(
        startLogin.redirectURI.path,
        'http://localhost:8080/openam/authorize',
        '/modialogin/api/start redirects to oidc-stub/authorize'
    );

    const state = startLogin.redirectURI.queryParams.state;
    const stateCookie = startLogin.headers['set-cookie'];
    assertThat(state, isDefined, '/modialogin/api/start state query-param is present')
    assertThat(stateCookie.length, 1, '/modialogin/api/start should set state-cookie')
    assertThat(
        startLogin.redirectURI.queryParams,
        {
            session: 'winssochain',
            authIndexType: 'service',
            authIndexValue: 'winssochain',
            response_type: 'code',
            scope: 'openid',
            client_id: 'foo',
            state,
            redirect_uri: encodeURIComponent('http://localhost:8082/modialogin/api/login'),
        },
        '/modialogin/api/start passes correct queryParams to idp'
    )

    const authorize = await fetch(startLogin.redirectURI.uri);
    assertThat(authorize.statusCode, 302, '/oidc-stub/authorize returns 302');
    assertThat(
        authorize.redirectURI.path,
        'http://localhost:8082/modialogin/api/login',
        '/oidc-stub/authorize redirects to modia-login/login'
    );
    const code = authorize.redirectURI.queryParams.code;
    assertThat(code, isDefined, '/oidc-stub/authorize code query-param is present');
    assertThat(
        authorize.redirectURI.queryParams.state,
        state,
        '/oidc-stub/authorize state query-param matches state sent in from modialogin'
    );

    const login = await fetch(authorize.redirectURI.uri, {
        'Cookie': stateCookie[0]
    });
    assertThat(login.statusCode, 302, '/modialogin/api/login returns 302');
    assertThat(
        login.redirectURI.path,
        `http://localhost:${port}/frontend/`,
        '/modialogin/login redirects to /frontend'
    );
    const loginCookies = login.headers['set-cookie'];
    const idtoken = loginCookies.find(cookie => cookie.startsWith('modia_ID_token'));
    const refreshtoken = loginCookies.find(cookie => cookie.startsWith('modia_refresh_token'));
    const removeStateCookie = loginCookies.find(cookie => cookie.startsWith(state));

    assertThat(idtoken, startsWith('modia_ID_token'), '/modialogin/api/login sets modia_ID_token cookie');
    assertThat(idtoken, hasLengthGreaterThen(80), 'modia_ID_token has some content');
    assertThat(idtoken, contains("Max-Age=3600;"), 'modia_ID_token is valid for 1 hour');

    assertThat(refreshtoken, startsWith('modia_refresh_token'), '/modialogin/api/login sets modia_refresh_token cookie');
    assertThat(refreshtoken, hasLengthGreaterThen(80), 'modia_refresh_token has some content');
    assertThat(refreshtoken, contains("Max-Age=72000;"), 'modia_ID_token is valid for 24 hours');

    assertThat(removeStateCookie, startsWith(state), '/modialogin/api/login sets modia_ID_token cookie');
    assertThat(removeStateCookie, contains('01 Jan 1970'), '/modialogin/api/login removes state cookie');

    return idtoken;
}

async function azureadloginflow(idtokencookie, port) {
    const cookies = { 'Cookie': idtokencookie };
    const initial = await fetch(`http://localhost:${port}/frontend/`, cookies);

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

    const startLogin = await fetch('http://localhost:8094'+initial.redirectURI.uri);

    assertThat(initial.statusCode, 302, '/frontend/oauth2/login returns 302');
    assertThat(
        startLogin.redirectURI.path,
        'http://localhost:8080/azuread/authorize',
        '/frontend/oauth2/login redirects to oidc-stub/azuread/authorize'
    );

    const state = startLogin.redirectURI.queryParams.state;
    const stateCookie = startLogin.headers['set-cookie'];
    assertThat(state, isDefined, '/frontend/oauth2/login state query-param is present')
    assertThat(stateCookie.length, 1, '/frontend/oauth2/login should set state-cookie')
    assertThat(
        startLogin.redirectURI.queryParams,
        {
            client_id: 'foo',
            response_type: 'code',
            response_mode: 'query',
            scope: 'openid+offline_access+api%3A%2F%2Ffoo%2F.default',
            state,
            redirect_uri: encodeURIComponent('http://localhost:8094/frontend/oauth2/callback'),
        },
        '/frontend/oauth2/login passes correct queryParams to idp'
    )

    const authorize = await fetch(startLogin.redirectURI.uri);
    assertThat(authorize.statusCode, 302, '/oidc-stub/azuread/authorize returns 302');
    assertThat(
        authorize.redirectURI.path,
        'http://localhost:8094/frontend/oauth2/callback',
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
        'Cookie': stateCookie[0]
    });
    assertThat(login.statusCode, 302, '/modialogin/api/login returns 302');
    assertThat(
        login.redirectURI.path,
        `http://localhost:${port}/frontend/`,
        '/modialogin/login redirects to /frontend'
    );
    const loginCookies = login.headers['set-cookie'];
    const idToken = loginCookies.find(cookie => cookie.startsWith('frontend_ID_TOKEN'));
    const accessToken = loginCookies.find(cookie => cookie.startsWith('frontend_ACCESS_TOKEN'));
    const refreshToken = loginCookies.find(cookie => cookie.startsWith('frontend_REFRESH_TOKEN'));
    assertThat(idToken, isDefined, '/frontend/oauth/callback sets id-token cookie');
    assertThat(accessToken, isDefined, '/frontend/oauth/callback sets access-token cookie');
    assertThat(refreshToken, isDefined, '/frontend/oauth/callback sets refresh-token cookie');

    const removeStateCookie = loginCookies.find(cookie => cookie.startsWith(state));

    assertThat(removeStateCookie, startsWith(state), '/frontend/oauth/callback sets modia_ID_token cookie');
    assertThat(removeStateCookie, contains('01 Jan 1970'), '/frontend/oauth/callback removes state cookie');

    return [idToken, accessToken, refreshToken].join(";");
}

test('attempts to get frontend resource should result in login-flow', async () => {
    const idtokencookie = await issologinflow("8083");
    const pageLoadAfterLogin = await fetch(`http://localhost:8083/frontend/`, {
        'Cookie': idtokencookie
    });

    assertThat(pageLoadAfterLogin.statusCode, 200, '/frontend returns 200');
    assertThat(pageLoadAfterLogin.body, contains('<!DOCTYPE html>'), '/frontend returns HTML')
});

test('attempts to get frontend resource should result in login-flow with dual–loging', async () => {
    const idtokencookie = await issologinflow("8094");
    let pageLoadAfterLogin = await fetch(`http://localhost:8094/frontend/`, {
        'Cookie': idtokencookie
    });
    assertThat(pageLoadAfterLogin.statusCode, 302, '/frontend returns 302 for azure ad login');

    const accesstokencookie = await azureadloginflow(idtokencookie, "8094", {
        'Cookie': idtokencookie
    });

    pageLoadAfterLogin = await fetch(`http://localhost:8094/frontend/`, {
        'Cookie': idtokencookie+';'+accesstokencookie,
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
    const tokens = await fetchJson('http://localhost:8080/openam/oauth/token', {}, {});
    const staticResource = await fetch('http://localhost:8083/frontend/static/css/index.css', {
        'Cookie': `modia_ID_token=${btoa(tokens.body['id_token'])};`
    });
    assertThat(staticResource.statusCode, 200, '/frontend returns 200');
    assertThat(staticResource.body, notContains('<!DOCTYPE html>'), 'css-file is not HTML')
});

test('frontend routing should return index.html', async () => {
    const tokens = await fetchJson('http://localhost:8080/openam/oauth/token', {}, {});
    const staticResource = await fetch('http://localhost:8083/frontend/some/spa-route?query=param', {
        'Cookie': `modia_ID_token=${btoa(tokens.body['id_token'])};`
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
    const tokens = await fetchJson('http://localhost:8080/openam/oauth/token', {}, {});
    const staticResource = await fetch('http://localhost:8083/frontend/static/css/missing.css',{
        'Cookie': `modia_ID_token=${btoa(tokens.body['id_token'])};`
    });
    assertThat(staticResource.statusCode, 404, '/frontend returns 404');
    assertThat(staticResource.body, notContains('<!DOCTYPE html>'), 'css-file is not HTML')
});

/**
 * Feature no longer supported.
 * All request that are proxyied must be by an authenticated user
 */
// test('proxying to open endpoint when not logged in', async () => {
//     const openEndpointWithoutCookie = await fetchJson('http://localhost:8083/frontend/proxy/open-endpoint/data');
//     assertThat(openEndpointWithoutCookie.statusCode, 200, '/frontend proxied to open endpoint');
//     assertThat(openEndpointWithoutCookie.body.path, '/data', '/frontend removed url prefix');
//     assertThat(openEndpointWithoutCookie.body.headers['cookie'], isNotDefined, '/frontend did not send cookie');
// });

test('proxying to open endpoint when logged in', async () => {
    const tokens = await fetchJson('http://localhost:8080/openam/oauth/token', {}, {});
    const openEndpointWithCookie = await fetchJson('http://localhost:8083/frontend/proxy/open-endpoint/data', {
        'Cookie': `modia_ID_token=${btoa(tokens.body['id_token'])};`
    });
    assertThat(openEndpointWithCookie.statusCode, 200, '/frontend proxied to open endpoint');
    assertThat(openEndpointWithCookie.body.path, '/data', '/frontend removed url prefix');
    assertThat(openEndpointWithCookie.body.headers['cookie'], isDefined, '/frontend did send cookie');
});

test('proxying to open endpoint that removes cookie when logged in', async () => {
    const tokens = await fetchJson('http://localhost:8080/openam/oauth/token', {}, {});
    const openEndpointWithCookie = await fetchJson('http://localhost:8083/frontend/proxy/open-endpoint-no-cookie/data', {
        'Cookie': `modia_ID_token=${btoa(tokens.body['id_token'])};`
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
        'http://localhost:8082/modialogin/api/start',
        '/frontend redirects to /modialogin/api/start'
    );
});

test('proxying to protected endpoint when logged in', async () => {
    const tokens = await fetchJson('http://localhost:8080/openam/oauth/token', {}, {});
    const protectedEndpoint = await fetchJson('http://localhost:8083/frontend/proxy/protected-endpoint/data', {
        'Cookie': `modia_ID_token=${btoa(tokens.body['id_token'])};`
    });
    assertThat(protectedEndpoint.statusCode, 200, '/frontend returns 200');
    assertThat(protectedEndpoint.body.path, '/data', '/frontend removed url prefix');
    assertThat(protectedEndpoint.body.headers['cookie'], isDefined, '/frontend did send cookie');
    assertThat(protectedEndpoint.body.headers['cookie'], startsWith('modia_ID_token'), '/frontend sent modia_ID_token cookie');
});

test('proxying to protected endpoint when logged in, and rewriting cookie name', async () => {
    const tokens = await fetchJson('http://localhost:8080/openam/oauth/token', {}, {});
    const protectedEndpoint = await fetchJson('http://localhost:8083/frontend/proxy/protected-endpoint-with-cookie-rewrite/data', {
        'Cookie': `modia_ID_token=${btoa(tokens.body['id_token'])};`
    });
    assertThat(protectedEndpoint.statusCode, 200, '/frontend returns 200');
    assertThat(protectedEndpoint.body.path, '/data', '/frontend removed url prefix');
    assertThat(protectedEndpoint.body.headers['cookie'], startsWith('ID_token'), '/frontend sent ID_token cookie');
    assertThat(protectedEndpoint.body.headers['cookie'], notContains('modia_ID_token'), '/frontend did not send modia_ID_token cookie');
});

test('proxying with obo-flow-directive exchanges the provided token', async () => {
    const openamTokens = await fetchJson('http://localhost:8080/openam/oauth/token', {}, {});
    const idtokencookie = `modia_ID_token=${btoa(openamTokens.body['id_token'])}`;
    const accesstokencookie = await azureadloginflow(idtokencookie, "8094", {
        'Cookie': idtokencookie
    });
    const openToken = openamTokens.body['id_token']
    const apiEndpoint = await fetchJson('http://localhost:8094/frontend/api/some/data/endpoint', {
        'Cookie': idtokencookie+';'+accesstokencookie,
    });

    assertThat(apiEndpoint.statusCode, 200, '/api returns 200')
    assertThat(apiEndpoint.body.path, '/modiapersonoversikt-api/some/data/endpoint', 'correct path is used by proxy')
    assertThat(apiEndpoint.body.headers['cookie'], equals(''), 'authorization header is set')
    assertThat(apiEndpoint.body.headers['authorization'], startsWith("Bearer "), 'authorization header is different from token')
    assertThat(apiEndpoint.body.headers['authorization'], notContains(openToken), 'authorization header is different from token')
});

test('environments variables are injected into nginx config', async () => {
    const tokens = await fetchJson('http://localhost:8080/openam/oauth/token', {}, {});
    const page = await fetch('http://localhost:8083/frontend/env-data', {
        'Cookie': `modia_ID_token=${btoa(tokens.body['id_token'])};`
    });
    assertThat(page.body, 'APP_NAME: frontend', 'Page contains environmentvariable value')
});

test('environments variables are injected into html config', async () => {
    const tokens = await fetchJson('http://localhost:8080/openam/oauth/token', {}, {});
    const page = await fetch('http://localhost:8083/frontend/', {
        'Cookie': `modia_ID_token=${btoa(tokens.body['id_token'])};`
    });
    assertThat(page.body, contains('&#36;env{APP_NAME}: frontend'), 'Page contains environmentvariable value')
});

test('csp directive is added to request', async () => {
    const tokens = await fetchJson('http://localhost:8080/openam/oauth/token', {}, {});
    const page = await fetch('http://localhost:8083/frontend/', {
        'Cookie': `modia_ID_token=${btoa(tokens.body['id_token'])};`
    });

    const cspPolicy = page.headers['content-security-policy-report-only'];
    assertThat(cspPolicy, isDefined, '/frontend has report-only CSP-policy');
    assertThat(cspPolicy, contains('script-src'), '/frontend has report-only CSP-policy');
});

test('referrer-policy is added to response', async () => {
    const tokens = await fetchJson('http://localhost:8080/openam/oauth/token', {}, {});
    const page = await fetch('http://localhost:8083/frontend/', {
        'Cookie': `modia_ID_token=${btoa(tokens.body['id_token'])};`
    });
    assertThat(page.headers['referrer-policy'], 'no-referrer', '/frontend has referrer-policy');
});