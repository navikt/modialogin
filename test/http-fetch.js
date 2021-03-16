const http = require('http');

function fetch(url, headers = {}) {
    const options = { headers };
    return new Promise((resolve, reject) => {
        http.get(url, options, (resp) => {
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
    });
}

function fetchJson(url) {
    return fetch(url)
        .then(({ statusCode, statusMessage, redirectURI, body }) => {
            if (body) {
                return { statusCode, statusMessage, redirectURI, body: JSON.parse(body) }
            } else {
                throw new Error(`${url} did not return json, body: ${body}`);
            }
        });
}

module.exports = {
    fetch,
    fetchJson
};