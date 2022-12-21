-- key, arg: consumer_num, interval_by_sec, refill_per_sec, limit, capacity
local current_timestamp = redis.call("TIME")
local current_seconds = current_timestamp[1]

local timestamp_key = KEYS[1] + "_timestamp"
local token_key = KEYS[1] + "_bucket"
local values = redis.call("mget", timestamp_key, token_key)

if values[1] == false or values[2] == false then
  redis.call("setex", timestamp_key, current_seconds, interval_by_sec)
  redis.call("setex", token_key, ARGV[2], interval_by_sec)
end





local current = redis.call('incrby', KEYS[1], ARGV[1])

if tonumber(current) == tonumber(ARGV[1]) then
  redis.call('expire', KEYS[1], ARGV[2])
end

return current