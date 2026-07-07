package com.loopers.interfaces.api.common.interceptor;

import com.loopers.config.QueueProperties;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.domain.user.UserModel;
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
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QueueTokenInterceptor implements HandlerInterceptor {

    public static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";

    private final WaitingQueueService waitingQueueService;
    private final RedissonClient redissonClient;
    private final QueueProperties queueProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        String token = request.getHeader(QUEUE_TOKEN_HEADER);
        if (!StringUtils.hasText(token)) {
            throw new CoreException(ErrorType.QUEUE_TOKEN_REQUIRED);
        }

        UserModel user = (UserModel) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        UUID userId = user.getId();

        String savedToken = waitingQueueService.findToken(userId)
            .orElseThrow(() -> new CoreException(ErrorType.QUEUE_TOKEN_REQUIRED));

        if (!savedToken.equals(token)) {
            throw new CoreException(ErrorType.QUEUE_TOKEN_REQUIRED);
        }

        RRateLimiter rateLimiter = redissonClient.getRateLimiter("order:ratelimit");
        rateLimiter.trySetRate(RateType.OVERALL, queueProperties.rateLimitPerSecond(), 1, RateIntervalUnit.SECONDS);
        if (!rateLimiter.tryAcquire()) {
            throw new CoreException(ErrorType.TOO_MANY_REQUESTS);
        }

        return true;
    }
}
