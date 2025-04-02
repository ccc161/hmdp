---秒杀券id
local voucherId = ARGV[1]
--用户id
local userId = ARGV[2]

--库存key
local stockKey = 'seckill:stock:' .. voucherId
--订单key
local orderKey = 'seckill:order:' .. voucherId

if (tonumber(redis.call('SISMEMBER', orderKey, userId)) == 1) then
    redis.call('SREM', orderKey, userId)
    redis.call('INCRBY', stockKey, 1)
    return 0
end

return 1