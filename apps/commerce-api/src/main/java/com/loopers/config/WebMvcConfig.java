package com.loopers.config;

import com.loopers.interfaces.api.common.interceptor.AdminAuthInterceptor;
import com.loopers.interfaces.api.common.interceptor.AuthInterceptor;
import com.loopers.interfaces.api.common.interceptor.PaymentRateLimitInterceptor;
import com.loopers.interfaces.api.common.interceptor.QueueTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final QueueTokenInterceptor queueTokenInterceptor;
    private final PaymentRateLimitInterceptor paymentRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns(
                "/api/v1/users/me",
                "/api/v1/users/me/password",
                "/api/v1/products/*/likes",
                "/api/v1/users/*/likes",
                "/api/v1/orders",
                "/api/v1/orders/*",
                "/api/v1/orders/*/cancel",
                "/api/v1/coupons/*/issue",
                "/api/v1/coupons/*/issue-requests",
                "/api/v1/coupons/issue-requests/*",
                "/api/v1/users/me/coupons",
                "/api/v1/payments",
                "/api/v1/queue/enter",
                "/api/v1/queue/position"
            );

        // 주문 API — 입장 토큰 검증 + Rate Limit (POST /api/v1/orders만 적용)
        registry.addInterceptor(queueTokenInterceptor)
            .addPathPatterns("/api/v1/orders");

        // 결제 API — Rate Limit (POST /api/v1/payments만 적용, callback 제외)
        registry.addInterceptor(paymentRateLimitInterceptor)
            .addPathPatterns("/api/v1/payments");

        // 어드민 API — LDAP 헤더 검증 (payment은 HMAC 보안이므로 제외)
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns(
                "/api-admin/v1/brands/**",
                "/api-admin/v1/products/**",
                "/api-admin/v1/orders/**",
                "/api-admin/v1/coupons/**"
            );
    }
}
