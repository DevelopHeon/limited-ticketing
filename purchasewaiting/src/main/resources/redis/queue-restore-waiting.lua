redis.call('ZREM', KEYS[2], ARGV[1])
redis.call('HDEL', KEYS[3], ARGV[1])
return redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
