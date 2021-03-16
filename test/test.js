const { test, assertThat, isDefined, startsWith, contains, hasLengthGreaterThen } = require('./test-lib');
const { fetch, fetchJson } = require('./http-fetch');

test('oidc-stub is running', async () => {
    const oidcConfig = await fetchJson('http://localhost:8080/.well-known/openid-configuration');
    assertThat(oidcConfig.statusCode, 200, 'oidcConfig is running');
});

test('modialogin is running', async () => {
    const loginapp = await fetch('http://localhost:8082/modialogin/internal/isAlive');
    assertThat(loginapp.statusCode, 200, 'loginapp is running');
});

test('frontendapp is running', async () => {
    const frontendapp = await fetch('http://localhost:8083/frontend/internal/isAlive');
    assertThat(frontendapp.statusCode, 200, 'frontendapp is running');
});

test('oidc-stub provides jwks', async () => {
    const jwks = await fetchJson('http://localhost:8080/.well-known/jwks.json');
    assertThat(jwks.body, isDefined, 'jwks.json returns a json body');
    assertThat(jwks.body.keys.length, 1, 'jwks has one key');
});

test('attempts to get frontend resource without trailing slash', async () => {
    const initial = await fetch('http://localhost:8083/frontend');
    assertThat(initial.statusCode, 301, 'frontend app returns 301');
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

    assertThat(refreshtoken, startsWith('modia_refresh_token'), '/modialogin/api/login sets modia_refresh_token cookie');
    assertThat(refreshtoken, hasLengthGreaterThen(80), 'modia_refresh_token has some content');

    assertThat(removeStateCookie, startsWith(state), '/modialogin/api/login sets modia_ID_token cookie');
    assertThat(removeStateCookie, contains('01 Jan 1970'), '/modialogin/api/login removes state cookie');

    const pageLoadAfterLogin = await fetch('http://localhost:8083/frontend/', {
        'Cookie': idtoken
    });
    assertThat(pageLoadAfterLogin.statusCode, 200, '/frontend returns 200');
});