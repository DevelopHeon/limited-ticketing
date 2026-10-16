if redis.call('ZSCORE', KEYS[2], ARGV[1]) then return 0 end
return redis.call('ZADD', KEYS[1], 'NX', ARGV[2], ARGV[1])
