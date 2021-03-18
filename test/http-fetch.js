const http = require('http');

function fetch(url, headers = {}, body) {
    const options = {headers, method: 'GET'};
    if (body) {
        options.method = 'POST';
    }
    return new Promise((resolve, reject) => {
        const req = http.request(url, options, (resp) => {
            const statusCode = resp.statusCode;
            const statusMessage = resp.statusMessage;
            const headers = resp.headers;
            let redirectURI = null;
            if (headers.location) {
                const location = headers.location;
                const fragments = location.split('?');
                const path = fragments[0];
                const queryString = fragments[1] || '';
                const queryParamStrings = queryString.split("&");
                const queryParams = queryParamStrings
                    .filter((str) => str.length > 0)
                    .map((str) => str.split("="))
                    .reduce((acc, [key, value]) => ({...acc, [key]: value}), {});

                redirectURI = { uri: location, path, queryParams };
            }

            let body = '';
            resp.on('data', (chunk) => {
                body += chunk;
            });

            resp.on('end', () => {
                resolve({ statusCode, statusMessage, redirectURI, headers, body });
            });
        })
            .on('error', (error) => {
                console.error('Error while fetching: ', url, headers);
                return reject(error);
            });
        if (body) {
            req.write(JSON.stringify(body));
        }
        req.end();
    });
}

function fetchJson(url, headers, body) {
    return fetch(url, headers, body)
        .then(({statusCode, statusMessage, redirectURI, body}) => {
            if (body) {
                return {statusCode, statusMessage, redirectURI, body: JSON.parse(body)}
            } else {
                throw new Error(`${url} did not return json, body: ${body}`);
            }
        });
}

module.exports = {
    fetch,
    fetchJson
};