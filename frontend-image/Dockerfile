FROM openresty/openresty:1.19.9.1-alpine-fat

# User env var is needed for luarocks to not complain.
ENV USER="root"

RUN apk add --no-cache --update bash
RUN luarocks install lua-resty-session \
    && luarocks install lua-resty-http \
    && luarocks install lua-resty-jwt \
    && luarocks install lua-resty-openidc

COPY nginx.nginx          /etc/nginx/conf.d/nginx.conf.template
COPY openresty.nginx      /usr/local/openresty/nginx/conf/nginx.conf
COPY oidc_protected.lua   /usr/local/openresty/nginx/
COPY mask_log.lua         /usr/local/openresty/nginx/
COPY start-nginx.sh       /usr/sbin/start-nginx

RUN mkdir -p /app
RUN mkdir -p /nginx

# Just a small default-app that show how the image may be used.
COPY default-app /app

EXPOSE 8080

RUN addgroup -S apprunner && adduser -S apprunner -G apprunner
RUN chown -R apprunner /app
RUN chown -R apprunner /nginx
RUN chown -R apprunner /etc/nginx/conf.d/
RUN chown apprunner /usr/sbin/start-nginx
RUN chmod +x /usr/sbin/start-nginx
USER apprunner

CMD ["start-nginx"]