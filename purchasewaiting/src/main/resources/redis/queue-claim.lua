local available = tonumber(ARGV[1]) - redis.call('ZCARD', KEYS[2])
if available <= 0 then return {} end
local count = math.min(available, tonumber(ARGV[2]))
local entries = redis.call('ZRANGE', KEYS[1], 0, count - 1)
local result = {}
for _, id in ipairs(entries) do
    redis.call('ZREM', KEYS[1], id)
    redis.call('ZADD', KEYS[2], ARGV[3], id)
    redis.call('HSET', KEYS[3], id, ARGV[4])
    table.insert(result, id)
    table.insert(result, ARGV[4])
end
return result
