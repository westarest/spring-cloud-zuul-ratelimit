/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository;

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.Rate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.*;

/**
 * @author Marcos Barbero
 * @author Liel Chayoun
 */
public class RedisRateLimiter extends AbstractNonBlockCacheRateLimiter {

    private final RateLimiterErrorHandler rateLimiterErrorHandler;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> redisQuotaScript;
    private final RedisScript<List> redisBurstScript;

    public RedisRateLimiter(final RateLimiterErrorHandler rateLimiterErrorHandler,
                            final StringRedisTemplate redisTemplate) {
        this.rateLimiterErrorHandler = rateLimiterErrorHandler;
        this.redisTemplate = redisTemplate;
        this.redisQuotaScript = getScript("quota.lua", Long.class);
        this.redisBurstScript = getScript("burst.lua", List.class);
    }

    @Override
    protected void calcRemainingLimit(final Long limit, final Duration refreshInterval,
                                      final Long requestTime, final String key, final Rate rate) {
        if (Objects.nonNull(limit)) {
            long usage = requestTime == null ? 1L : 0L;
            List<Long> result = Arrays.asList(0L, 0L);
            Long timeToNextRefill = refreshInterval.getSeconds() / limit;
            Long refillPerMsec = limit * 1000 / refreshInterval.getSeconds();
            try {
                Long currentRedisTime = getRedisTime();
                result = redisTemplate.execute(redisBurstScript,
                        Collections.singletonList(key),
                        Long.toString(usage),
                        Long.toString(refillPerMsec),
                        Long.toString(rate.getCapacity()),
                        Long.toString(currentRedisTime)
                        );
//                assert result != null;
                timeToNextRefill = timeToNextRefill - (currentRedisTime > result.get(1) ? (currentRedisTime - result.get(1)) : 0L);

            } catch (RuntimeException e) {
                String msg = "Failed retrieving rate for " + key + ", will return the current value";
                rateLimiterErrorHandler.handleError(msg, e);

            }
//            Long remaining = calcRemaining(limit, refreshInterval, usage, key, rate);
            rate.setRemaining(result.get(0));
            rate.setReset(timeToNextRefill);
        }
    }
    private Long getRedisTime() {
        return redisTemplate.execute((RedisCallback<Long>) conn -> conn.time()/1000 );
    }

    @Override
    protected void calcRemainingQuota(final Long quota, final Duration refreshInterval,
                                      final Long requestTime, final String key, final Rate rate) {
        if (Objects.nonNull(quota)) {
            String quotaKey = key + QUOTA_SUFFIX;
            long usage = requestTime != null ? requestTime : 0L;
            rate.setReset(refreshInterval.toMillis());
            Long current = 0L;
            try {
                current = redisTemplate.execute(redisQuotaScript, Collections.singletonList(quotaKey), Long.toString(usage),
                        Long.toString(refreshInterval.getSeconds()));
            } catch (RuntimeException e) {
                String msg = "Failed retrieving rate for " + quotaKey + ", will return the current value";
                rateLimiterErrorHandler.handleError(msg, e);
            }
            Long remaining = Math.max(-1, quota- (current != null ? current.intValue() : 0));
//            Long remaining = calcRemaining(quota, refreshInterval, usage, quotaKey, rate);
            rate.setRemainingQuota(remaining);
        }
    }


    private <T> RedisScript<T> getScript(String scriptName, Class<T> tClass) {

        DefaultRedisScript<T> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("/scripts/"+ scriptName));
        redisScript.setResultType(tClass);
        return redisScript;
    }
}
