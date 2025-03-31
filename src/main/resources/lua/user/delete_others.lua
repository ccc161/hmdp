local tokens_key = KEYS[1]
local token_key = ARGV[1]

if redis.call("EXISTS", token_key) == 0 then
    -- 删除zset中的tokenKey成员（如果存在）
    redis.call("ZREM", tokens_key, token_key)
    return 1
end

-- 检查token是否在zset中
if not redis.call("ZSCORE", tokens_key, token_key) then
    -- 删除tokenKey及zset中的成员
    redis.call("DEL", token_key)
    redis.call("ZREM", tokens_key, token_key)
    return 2
end

local tokens = redis.call("ZRANGE", tokens_key, 0, -1)
local delete_tokens = {}
for _, token in ipairs(tokens) do
    if token ~= token_key then
        table.insert(delete_tokens, token)
    end
end
if #delete_tokens > 0 then
    redis.call("DEL", unpack(delete_tokens))
    redis.call("ZREM", tokens_key, unpack(delete_tokens))
end
