package com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.filters.pre;

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.RateLimiterErrorHandler;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.repository.RedisRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support.RateLimitConstants.HEADER_REMAINING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * @author Marcos Barbero
 * @since 2017-06-30
 */
public class RedisRateLimitPreFilterTest extends BaseRateLimitPreFilterTest {

    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @Override
    public void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        RateLimiterErrorHandler rateLimiterErrorHandler = mock(RateLimiterErrorHandler.class);
        this.setRateLimiter(new RedisRateLimiter(rateLimiterErrorHandler, this.redisTemplate));
        doReturn(1L).when(redisTemplate).execute((RedisCallback<Object>) any());
        doReturn(Arrays.asList(1L, 1L))
                .when(redisTemplate).execute(any(), anyList(), anyString(), anyString(), anyString(), anyString());
        super.setUp();
    }

    @Test
    @Override
    @SuppressWarnings("unchecked")
    public void testRateLimitExceedCapacity() throws Exception {
        doReturn(3L)
                .when(redisTemplate).execute(any(), anyList(), anyString(), anyString());
        doReturn(Arrays.asList(-1L, 1L))
                .when(redisTemplate).execute(any(), anyList(), anyString(), anyString(), anyString(), anyString());

        super.testRateLimitExceedCapacity();
    }

    @Test
    @Override
    @SuppressWarnings("unchecked")
    public void testRateLimit() throws Exception {
        doReturn(1L, 2L)
                .when(redisTemplate).execute(any(), anyList(), anyString(), anyString());
        doReturn(Arrays.asList(0L, 2L))
                .when(redisTemplate).execute(any(), anyList(), anyString(), anyString(), anyString(), anyString());
        doReturn(1L).when(redisTemplate).execute((RedisCallback<Object>) any());


        this.request.setRequestURI("/serviceA");
        this.request.setRemoteAddr("10.0.0.100");

        assertTrue(this.filter.shouldFilter());

        for (int i = 0; i < 2; i++) {
            this.filter.run();
        }

        String key = "-null_serviceA_10.0.0.100_anonymous";
        String remaining = this.response.getHeader(HEADER_REMAINING + key);
        assertEquals("0", remaining);

        TimeUnit.SECONDS.sleep(2);

        doReturn(Arrays.asList(1L, 2L))
                .when(redisTemplate).execute(any(), anyList(), anyString(), anyString(), anyString(), anyString());

        this.filter.run();
        remaining = this.response.getHeader(HEADER_REMAINING + key);
        assertEquals("1", remaining);
    }

    @Test
    public void testShouldReturnCorrectRateRemainingValue() {
        doReturn(Arrays.asList(1L, 2L), Arrays.asList(0L,2L))
                .when(redisTemplate).execute(any(), anyList(), anyString(), anyString(), anyString(), anyString());

        this.request.setRequestURI("/serviceA");
        this.request.setRemoteAddr("10.0.0.100");
        this.request.setMethod("GET");

        assertTrue(this.filter.shouldFilter());

        String key = "-null_serviceA_10.0.0.100_anonymous_GET";

        long requestCounter = 2;
        for (int i = 0; i < 2; i++) {
            this.filter.run();
            Long remaining = Long.valueOf(Objects.requireNonNull(this.response.getHeader(HEADER_REMAINING + key)));
            assertEquals(--requestCounter, remaining);
        }
    }
}
