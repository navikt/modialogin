local session, session_error = require("resty.session").start()
local cookies_to_set = {};
local cookie_secure = "secure; "
local cookie_domain = ""
local function set_cookie_if_changed(cookie_name, cookie_value)
    if cookie_value and tostring(cookie_value) ~= ngx.var["cookie_" .. cookie_name] then
        local cookie_string = cookie_name .. '=' .. tostring(cookie_value) .. '; ' .. cookie_secure .. cookie_domain .. 'path=/; SameSite=Lax; HttpOnly'
        table.insert(cookies_to_set, cookie_string);
        return true
    end
end
local function remove_cookie(cookie_name)
    local cookie_string = cookie_name .. "=; expires=Thu, Jan 01 1970 00:00:00 UTC; " .. cookie_secure .. cookie_domain .. 'path=/; SameSite=Lax; HttpOnly'
    table.insert(cookies_to_set, cookie_string);
end
local function set_changed_cookies()
    if table.getn(cookies_to_set) > 0 then
        ngx.header['Set-Cookie'] = cookies_to_set
    end
end

if session == nil then
    ngx.status = 500
    ngx.header.content_type = 'text/plain';
    ngx.say("Problem with getting the session: ", session_error)
    ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
end

local app_name = ngx.var.app_name
local app_version = ngx.var.app_version
local idp_discovery_url = ngx.var.idp_discovery_url
local idp_client_id = ngx.var.idp_client_id
local idp_client_secret = ngx.var.idp_client_secret
local start_uri = "/" .. app_name .. "/api/start"
local login_uri = "/" .. app_name .. "/api/login"
local complete_uri = "/" .. app_name .. "/api/complete"

-- If call to /api/start
if ngx.var.uri == start_uri then
    local target_url = ngx.req.get_uri_args().url
    -- Verifying that `uri` is set, show 500 error if not.
    if target_url == nil then
        local err = "No url-queryparam specified"
        ngx.log(ngx.ERR, err)
        ngx.status = 500
        ngx.header.content_type = 'text/plain';
        ngx.say(err)
        ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
        return
    else
        -- If `uri` is set, store it in session for future redirect
        ngx.log(ngx.INFO, "Storing original url in session: "..target_url)
        session.data.original_target_url = target_url
    end
else
    -- must reset complete_uri, if not we are forced into an infinte loop by resty.openidc
    -- complete_uri should only ever be set during /api/start
    complete_uri = nil
end

-- Options for resty.openidc
local opts = {
    redirect_uri = login_uri,
    discovery = idp_discovery_url,
    client_id = idp_client_id,
    client_secret = idp_client_secret,
    scope = "openid",
    jwk_expires_in = 24 * 60 * 60,
    access_token_expires_leeway = 240,
    auth_accept_token_as = "cookie:modia_ID_token",
    token_endpoint_auth_method = "client_secret_basic",
    authorization_params = {
        session = "winssochain",
        authIndexType = "service",
        authIndexValue = "winssochain"
    }
}

-- Do not reautenticate XHR/fetch request
local unauth_action
-- dont reautenticate on XHR
if ngx.var.http_x_requested_with == "XMLHttpRequest" then
    unauth_action = 'pass'
end

-- Authentication start
-- /api/start -> 301 authorize
-- /api/login -> fetch id_token, 301 complete
local res, err = require("resty.openidc").authenticate(opts, complete_uri, unauth_action, session)
-- /api/complete -> set cookie, 301 original_url
-- Authentication complete
-- If the code gets here it mean the request is authenticated.
-- If not `authenticate()` would have redirected the request to the IDP.
if err then
    ngx.status = 500
    ngx.header.content_type = 'text/plain';
    ngx.say(err)
    ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
end

-- Request is authenticated
-- Get original url, id_token and refresh_token from session, set cookies and redirect.
local original_target = session.data.original_target_url
if original_target then
    if session.data.enc_id_token and set_cookie_if_changed("modia_ID_token", session.data.enc_id_token) then
        session.data.access_token_expiration = session.data.id_token.exp
        set_cookie_if_changed("modia_refresh_token", session.data.refresh_token)
    end
    remove_cookie("session")
    set_changed_cookies()
    ngx.redirect(original_target)
end