FROM openresty/openresty:alpine-fat

# User env var is needed for luarocks to not complain.
ENV APP_DIR="/app" \
    USER="root"

RUN apk add --no-cache --update bash
RUN luarocks install lua-resty-session \
    && luarocks install lua-resty-http \
    && luarocks install lua-resty-jwt \
    && luarocks install lua-resty-openidc

COPY nginx.nginx          /etc/nginx/conf.d/nginx.conf.template
COPY oidc_protected.lua   /usr/local/openresty/nginx/
COPY start-nginx.sh       /usr/sbin/start-nginx
RUN chmod u+x /usr/sbin/start-nginx
RUN mkdir -p /nginx
RUN mkdir -p /app

# Just a small default-app that show how the image may be used.
COPY default-app /app

EXPOSE 8080

CMD ["start-nginx"]