-- key, arg: usage, refill_per_msec, capacity, current_seconds
--return: remain, prev_timestamp
-- Attention! Require lua > 5.3

local timestamp_key = KEYS[1] .. "_timestamp"
local token_key = KEYS[1] .. "_bucket"
local usage = tonumber(ARGV[1])
local refill_per_msec = tonumber(ARGV[2])
local capacity = tonumber(ARGV[3])
local current_seconds = tonumber(ARGV[4])


local values = redis.call("mget", timestamp_key, token_key)
local ret = {}
if values[1] == false or values[2] == false then
  --todo: set TTL
  local remain = capacity - usage
  redis.call("set", timestamp_key, current_seconds)
  redis.call("set", token_key, remain)
  ret[1] = remain
  ret[2] = current_seconds
  return ret
end

--bucket exists
local prev_timestamp = tonumber(values[1])
ret[2] = prev_timestamp
local remain = tonumber(values[2])

local refill_num = 0
if current_seconds > prev_timestamp then
  refill_num = math.floor((current_seconds - prev_timestamp) * refill_per_msec / 1000)
end


if refill_num + remain > capacity then
  refill_num = capacity - remain
end

--refill bucket
if refill_num > 0 then
  redis.call("set", timestamp_key, current_seconds)
  ret[2] = current_seconds
  remain = redis.call("incrby", token_key, refill_num)
end

if remain < usage then
  ret[1] = -1
  return ret
end

--usager token
remain = redis.call("decrby", token_key, usage)
if remain == false then
  remain = -1
end

redis.call("set", token_key, remain)
ret[1] = remain
return ret