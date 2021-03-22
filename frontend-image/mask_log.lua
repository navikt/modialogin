local mask = function (match)
    local prefix = match[0]:sub(0, 6)
    local suffix_length = match[0]:len() - prefix:len()
    local suffix = string.rep("*", suffix_length)
    return prefix .. suffix
end

local masked, n, err = ngx.re.gsub(ngx.arg[1], "(\\d{6,})", mask)
return masked