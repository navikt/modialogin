#!/bin/bash
set -e

# Better shutdown handling
_shutdown_() {
  # https://github.com/kubernetes/contrib/issues/1140
  # https://github.com/kubernetes/kubernetes/issues/43576
  # https://github.com/kubernetes/kubernetes/issues/64510
  # https://nav-it.slack.com/archives/C5KUST8N6/p1543497847341300
  echo "shutdown initialized, allowing incoming requests for 5 seconds before continuing"
  sleep 5
  nginx -s quit
  wait "$pid"
}
trap _shutdown_ SIGTERM

# Setting default values for environment variables
export AUTH_TOKEN_RESOLVER="${AUTH_TOKEN_RESOLVER:-cookie:modia_ID_token}"
export APP_VERSION="${APP_VERSION:localhost}"

# Setting nginx resolver, so that containers can communicate.
export RESOLVER=$(cat /etc/resolv.conf | grep -v '^#' | grep -m 1 nameserver | awk '{print $2}') # Picking the first nameserver.

echo "Startup: ${APP_NAME}:${APP_VERSION}"
echo "Resolver: ${RESOLVER}"

# fetching env from vault file.
if test -d /var/run/secrets/nais.io/vault;
then
    for FILE in /var/run/secrets/nais.io/vault/*.env
    do
        for line in $(cat ${FILE}); do
            # shellcheck disable=SC2006
            echo "exporting secret: `echo "${line}" | cut -d '=' -f 1`"
            export "${line}"
        done
    done
fi

envsubst '$APP_NAME $APP_VERSION $IDP_DISCOVERY_URL $IDP_CLIENT_ID $IDP_CLIENT_SECRET $RESOLVER' < /etc/nginx/conf.d/nginx.conf.template > /etc/nginx/conf.d/default.conf

echo "---------------------------"
cat /etc/nginx/conf.d/default.conf
echo "---------------------------"

/usr/local/openresty/bin/openresty -g 'daemon off;'
pid=$!
wait "$pid"