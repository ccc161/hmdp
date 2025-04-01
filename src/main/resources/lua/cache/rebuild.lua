local lock_key = KEYS[1]
local data_key = KEYS[2]
local lock_value = ARGV[1]
local data_value = ARGV[2]
if redis.call("GET", lock_key) == lock_value then
    redis.call("SET", data_key, data_value)
end