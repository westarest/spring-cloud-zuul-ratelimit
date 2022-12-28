package com.marcosbarbero.tests.config;

import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.config.properties.RateLimitProperties;
import com.marcosbarbero.cloud.autoconfigure.zuul.ratelimit.support.DefaultRateLimitUtils;

import javax.servlet.http.HttpServletRequest;

public class AAMRateLimitUtils extends DefaultRateLimitUtils {
    public AAMRateLimitUtils(final RateLimitProperties properties) {
        super(properties);
    }
    @Override
    public String getUser(final HttpServletRequest request) {
        return request.getHeader("X-AAM-USER") != null? request.getHeader("X-AAM-USER"): getAnonymousUser();
    }

}
