if redis.call('GET', KEYS[1]) ~= ARGV[1] then
    return {'STALE'}
end
local value = redis.call('GET', KEYS[2])
if value then
    return {'HIT', value}
end
if redis.call('SET', KEYS[3], ARGV[2], 'NX', 'PX', ARGV[3]) then
    local time = redis.call('TIME')
    local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
    return {'CLAIMED', ARGV[1], ARGV[2], string.format('%.0f', now + tonumber(ARGV[4]))}
end
return {'BUSY'}
