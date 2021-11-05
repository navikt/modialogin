const { test, assertThat, verify, setup, retry, isDefined, isNotDefined, startsWith, contains, notContains, hasLengthGreaterThen } = require('./test-lib');
const { fetch, fetchJson } = require('./http-fetch');

setup('oidc-stub is running', retry({ retry: 10, interval: 2}, async () => {
    const oidcConfig = await fetchJson('http://localhost:8080/.well-known/openid-configuration');
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
    const jwks = await fetchJson('http://localhost:8080/.well-known/jwks.json');
    assertThat(jwks.body, isDefined, 'jwks.json returns a json body');
    assertThat(jwks.body.keys.length, 1, 'jwks has one key');
});

test('attempts to get frontend resource without trailing slash', async () => {
    const initial = await fetch('http://localhost:8083/frontend');
    assertThat(initial.statusCode, 301, '/frontend returns 301');
    assertThat(initial.redirectURI.path, 'frontend/', 'appends trailing slash');
});

test('attempts to get frontend resource should result in login-flow', async () => {
    const initial = await fetch('http://localhost:8083/frontend/');

    assertThat(initial.statusCode, 302, '/frontend returns 302');
    assertThat(
        initial.redirectURI.path,
        'http://localhost:8082/modialogin/api/start',
        '/frontend redirects to /modialogin/api/start'
    );
    assertThat(
        initial.redirectURI.queryParams.url,
        encodeURIComponent('http://localhost:8083/frontend/'),
        '/frontend redirect passes original url encoded in queryparameter'
    );

    const startLogin = await fetch(initial.redirectURI.uri);

    assertThat(initial.statusCode, 302, '/modialogin/api/start returns 302');
    assertThat(
        startLogin.redirectURI.path,
        'http://localhost:8080/authorize',
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
        'http://localhost:8083/frontend/',
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

    const pageLoadAfterLogin = await fetch('http://localhost:8083/frontend/', {
        'Cookie': idtoken
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
    const tokens = await fetchJson('http://localhost:8080/oauth/token', {}, {});
    const staticResource = await fetch('http://localhost:8083/frontend/static/css/index.css', {
        'Cookie': `modia_ID_token=${tokens.body['id_token']};`
    });
    assertThat(staticResource.statusCode, 200, '/frontend returns 302');
    assertThat(staticResource.headers['referrer-policy'], 'no-referrer', '/frontend has referrer-policy');
    assertThat(staticResource.body, notContains('<!DOCTYPE html>'), 'css-file is not HTML')
});

test('missing static resource returns 404 instead of fallback to index.html', async () => {
    const tokens = await fetchJson('http://localhost:8080/oauth/token', {}, {});
    const staticResource = await fetch('http://localhost:8083/frontend/static/css/missing.css',{
        'Cookie': `modia_ID_token=${tokens.body['id_token']};`
    });
    assertThat(staticResource.statusCode, 404, '/frontend returns 404');
    assertThat(staticResource.body, notContains('<!DOCTYPE html>'), 'css-file is not HTML')
});

test('proxying to open endpoint when not logged in', async () => {
    const openEndpointWithoutCookie = await fetchJson('http://localhost:8083/frontend/proxy/open-endpoint/data');
    assertThat(openEndpointWithoutCookie.statusCode, 200, '/frontend proxied to open endpoint');
    assertThat(openEndpointWithoutCookie.body.path, '/data', '/frontend removed url prefix');
    assertThat(openEndpointWithoutCookie.body.headers['cookie'], isNotDefined, '/frontend did not send cookie');
});

test('proxying to open endpoint when logged in', async () => {
    const tokens = await fetchJson('http://localhost:8080/oauth/token', {}, {});
    const openEndpointWithCookie = await fetchJson('http://localhost:8083/frontend/proxy/open-endpoint/data', {
        'Cookie': tokens.body['id_token']
    });
    assertThat(openEndpointWithCookie.statusCode, 200, '/frontend proxied to open endpoint');
    assertThat(openEndpointWithCookie.body.path, '/data', '/frontend removed url prefix');
    assertThat(openEndpointWithCookie.body.headers['cookie'], isDefined, '/frontend did send cookie');
});

test('proxying to open endpoint that removes cookie when logged in', async () => {
    const tokens = await fetchJson('http://localhost:8080/oauth/token', {}, {});
    const openEndpointWithCookie = await fetchJson('http://localhost:8083/frontend/proxy/open-endpoint-no-cookie/data', {
        'Cookie': `modia_ID_token=${tokens.body['id_token']};`
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
    const tokens = await fetchJson('http://localhost:8080/oauth/token', {}, {});
    const protectedEndpoint = await fetchJson('http://localhost:8083/frontend/proxy/protected-endpoint/data', {
        'Cookie': `modia_ID_token=${tokens.body['id_token']};`
    });
    assertThat(protectedEndpoint.statusCode, 200, '/frontend returns 200');
    assertThat(protectedEndpoint.body.path, '/data', '/frontend removed url prefix');
    assertThat(protectedEndpoint.body.headers['cookie'], isDefined, '/frontend did send cookie');
    assertThat(protectedEndpoint.body.headers['cookie'], startsWith('modia_ID_token'), '/frontend sent modia_ID_token cookie');
});

test('proxying to protected endpoint when logged in, and rewriting cookie name', async () => {
    const tokens = await fetchJson('http://localhost:8080/oauth/token', {}, {});
    const protectedEndpoint = await fetchJson('http://localhost:8083/frontend/proxy/protected-endpoint-with-cookie-rewrite/data', {
        'Cookie': `modia_ID_token=${tokens.body['id_token']};`
    });
    assertThat(protectedEndpoint.statusCode, 200, '/frontend returns 200');
    assertThat(protectedEndpoint.body.path, '/data', '/frontend removed url prefix');
    assertThat(protectedEndpoint.body.headers['cookie'], startsWith('ID_token'), '/frontend sent ID_token cookie');
    assertThat(protectedEndpoint.body.headers['cookie'], notContains('modia_ID_token'), '/frontend did not send modia_ID_token cookie');
});

test('environments variables are injected into nginx config', async () => {
    const page = await fetch('http://localhost:8083/frontend/env-data');
    assertThat(page.body, 'APP_NAME: frontend', 'Page contains environmentvariable value')
});

test('environments variables are injected into html config', async () => {
    const tokens = await fetchJson('http://localhost:8080/oauth/token', {}, {});
    const page = await fetch('http://localhost:8083/frontend/', {
        'Cookie': `modia_ID_token=${tokens.body['id_token']};`
    });
    assertThat(page.body, contains('&amp;{APP_NAME}: frontend'), 'Page contains environmentvariable value')
});

test('csp directive is added to request', async () => {
    const tokens = await fetchJson('http://localhost:8080/oauth/token', {}, {});
    const page = await fetch('http://localhost:8083/frontend/', {
        'Cookie': `modia_ID_token=${tokens.body['id_token']};`
    });

    const cspPolicy = page.headers['content-security-policy-report-only'];
    assertThat(cspPolicy, isDefined, '/frontend has report-only CSP-policy');
    assertThat(cspPolicy, contains('script-src'), '/frontend has report-only CSP-policy');
});