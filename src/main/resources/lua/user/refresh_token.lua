-- 0 刷新成功
-- 1 token不在redis中
-- 2 token不在redis的zset中
local tokens_key = KEYS[1]
local token_key = KEYS[2]
local expire_millis = tonumber(ARGV[1])

-- 检查tokenKey是否存在
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

-- 双重验证通过，刷新时间
local time = redis.call("TIME")
local current_millis = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000.0)
redis.call("PEXPIRE", token_key, expire_millis)
redis.call('ZADD', tokens_key, current_millis, token_key)
return 0

