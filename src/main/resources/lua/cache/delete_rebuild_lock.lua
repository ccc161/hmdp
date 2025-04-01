local lock_key = KEYS[1]
local lock_value = ARGV[1]
if redis.call('GET', lock_key) == lock_value then
    return redis.call('DEL', lock_key)
else
    return 0
end