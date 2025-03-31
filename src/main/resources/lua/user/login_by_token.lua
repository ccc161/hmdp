local tokens_key = KEYS[1]
local token_key = KEYS[2]
local last_refresh_timestamp_millis = tonumber(ARGV[1])
local expire_time_millis = tonumber(ARGV[2])
local max_devices = tonumber(ARGV[3])
local userMap = cjson.decode(ARGV[4])


-- 构建HSET命令参数列表
local hset_args = { token_key }
for key, value in pairs(userMap) do
    table.insert(hset_args, key)
    table.insert(hset_args, value)
end

-- 执行HSET操作存储Hash数据
redis.call('HSET', unpack(hset_args))

-- 设置键的过期时间（毫秒）
redis.call('PEXPIRE', token_key, expire_time_millis)

-- 添加新token到有序集合
redis.call('ZADD', tokens_key, last_refresh_timestamp_millis, token_key)

-- 超过最大设备数时删除最旧token
local device_count = redis.call("ZCARD", tokens_key)
if device_count > max_devices then
    -- 获取最旧的token（按分数升序取第一个）
    local oldest_tokens = redis.call('ZRANGE', tokens_key, 0, 0, 'WITHSCORES')
    if oldest_tokens and #oldest_tokens > 0 then
        local oldest_token = oldest_tokens[1]
        -- 从有序集合中移除
        redis.call('ZREM', tokens_key, oldest_token)
        -- 删除对应的token键
        redis.call('DEL', oldest_token)
    end
end