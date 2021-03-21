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

# Utility for checking if all variables are defined in environment
requireEnv() {
  MISSING=0
  IFS=' '
  read -ra VARS <<<$1
  echo "Found ${#VARS[*]} required environment variables."
  for name in "${VARS[@]}"; do
    value=$(eval "echo $name")
    if [[ -z $value ]]; then
      echo "Missing! $name not set"
      MISSING=1
    fi
  done
  if [ $MISSING == 1 ]; then
    exit 1
  fi
}


# Setting nginx resolver, so that containers can resolve domain names correctly.
export RESOLVER=$(cat /etc/resolv.conf | grep -v '^#' | grep -m 1 nameserver | awk '{print $2}') # Picking the first nameserver.

# Settings default environment variabels
export APP_PORT="${APP_PORT:-443}"
export CSP_DIRECTIVES="${CSP_DIRECTIVES:-default-src: 'self;'}"
export CSP_REPORT_ONLY="${CSP_REPORT_ONLY:-false}"


echo "Startup: ${APP_NAME}:${APP_VERSION}"
echo "Resolver: ${RESOLVER}"

# Exporting vault environments files
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

declare -a ENV_VARIABLES=(
  '$APP_PORT'
  '$APP_NAME'
  '$APP_VERSION'
  '$IDP_DISCOVERY_URL'
  '$IDP_CLIENT_ID'
  '$DELEGATED_LOGIN_URL'
  '$AUTH_TOKEN_RESOLVER'
  '$RESOLVER'
  '$CSP_DIRECTIVES'
  '$CSP_REPORT_ONLY'
)
ALL_ENV_VARIABLES="${ENV_VARIABLES[*]}"
# Checks all required variables are defined in environment
requireEnv "$ALL_ENV_VARIABLES"
# Inject environment variables into nginx.conf
envsubst "$ALL_ENV_VARIABLES" < /etc/nginx/conf.d/nginx.conf.template > /etc/nginx/conf.d/default.conf
echo "---------------------------"
cat /etc/nginx/conf.d/default.conf
echo "---------------------------"

# Copying template folders so that we can modifiy files without changing externally mounted volumes
cp -r /app/. /app-source
cp -r /nginx/. /nginx-source

# Find all environment variables starting with: APP_
export APP_VARIABLES=$(echo $(env | cut -d= -f1 | grep "^APP_" | sed -e 's/^/\$/'))

echo "Startup inject envs:"
echo $APP_VARIABLES

# Inject environment variabels starting with: APP_ into all static resources and nginx-config
find /app-source -type f -regex '.*\.\(js\|css\|html\|json\|map\)' -print0 |
while IFS= read -r -d '' file; do
  echo "Injecting environment variables into $file"
  envsubst "$APP_VARIABLES" < $file > $file.tmp
  mv $file.tmp $file
done
find /nginx-source -type f -regex '.*\.nginx' -print0 |
while IFS= read -r -d '' file; do
  echo "Injecting environment variables into $file"
  envsubst "$APP_VARIABLES" < $file > $file.tmp
  mv $file.tmp $file
done

/usr/local/openresty/bin/openresty -g 'daemon off;'
pid=$!
wait "$pid"
