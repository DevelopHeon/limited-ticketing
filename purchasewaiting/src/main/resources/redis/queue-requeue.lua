local token = redis.call('HGET', KEYS[3], ARGV[1])
if token and token ~= ARGV[2] then return 0 end
if not token and redis.call('ZSCORE', KEYS[2], ARGV[1]) then return 0 end
redis.call('ZREM', KEYS[2], ARGV[1])
redis.call('HDEL', KEYS[3], ARGV[1])
redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
return 1
