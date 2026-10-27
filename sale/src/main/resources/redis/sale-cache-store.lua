if redis.call('GET', KEYS[1]) ~= ARGV[1] or redis.call('GET', KEYS[3]) ~= ARGV[2] then
    return 0
end
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local ttl = tonumber(ARGV[3]) - now
if ttl <= 0 then
    return 0
end
redis.call('SET', KEYS[2], ARGV[4], 'PX', ttl)
return 1
