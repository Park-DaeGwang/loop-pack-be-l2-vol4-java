package com.loopers.interfaces.api.common.interceptor;

import com.loopers.domain.queue.QueueDynamicConfig;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class PaymentRateLimitInterceptor implements HandlerInterceptor {

    private final RedissonClient redissonClient;
    private final QueueDynamicConfig queueDynamicConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter("payment:ratelimit");
            rateLimiter.trySetRate(RateType.OVERALL, queueDynamicConfig.paymentRateLimitPerSecond(), 1, RateIntervalUnit.SECONDS);
            if (!rateLimiter.tryAcquire()) {
                throw new CoreException(ErrorType.TOO_MANY_REQUESTS);
            }
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE);
        }
        return true;
    }
}
