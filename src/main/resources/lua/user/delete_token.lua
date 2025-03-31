local tokens_key = KEYS[1]
local token_key = ARGV[1]

redis.call("DEL", token_key)
redis.call("ZREM", tokens_key, token_key)