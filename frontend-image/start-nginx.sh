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

# Setting nginx resolver, so that containers can communicate.
export RESOLVER=$(cat /etc/resolv.conf | grep -v '^#' | grep -m 1 nameserver | awk '{print $2}') # Picking the first nameserver.

echo "Startup: ${APP_NAME}:${APP_VERSION}"
echo "Resolver: ${RESOLVER}"

# fetching env from vault file.
if test -d /var/run/secrets/nais.io/vault;
then
    for FILE in $(find /var/run/secrets/nais.io/vault -maxdepth 1 -name "*.env")
    do
        _oldIFS=$IFS
        IFS='
'
        for line in $(cat "$FILE"); do
            _key=${line%%=*}
            _val=${line#*=}

            if test "$_key" != "$line"
            then
                echo "- exporting $_key"
            else
                echo "- (warn) exporting contents of $FILE which is not formatted as KEY=VALUE"
            fi

            export "$_key"="$(echo "$_val"|sed -e "s/^['\"]//" -e "s/['\"]$//")"
        done
        IFS=$_oldIFS
    done
fi

envsubst '$APP_NAME $APP_VERSION $IDP_DISCOVERY_URL $IDP_CLIENT_ID $DELEGATED_LOGIN_URL $AUTH_TOKEN_RESOLVER $RESOLVER' < /etc/nginx/conf.d/nginx.conf.template > /etc/nginx/conf.d/default.conf

echo "---------------------------"
cat /etc/nginx/conf.d/default.conf
echo "---------------------------"

/usr/local/openresty/bin/openresty -g 'daemon off;'
pid=$!
wait "$pid"