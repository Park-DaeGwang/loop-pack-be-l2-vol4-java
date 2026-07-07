package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.QueueDynamicConfig;
import com.loopers.interfaces.api.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-admin/v1/queue")
@RequiredArgsConstructor
public class QueueAdminV1Controller {

    private final QueueDynamicConfig queueDynamicConfig;

    @GetMapping("/config")
    public ApiResponse<QueueAdminV1Dto.ConfigResponse> getConfig() {
        return ApiResponse.success(new QueueAdminV1Dto.ConfigResponse(
            queueDynamicConfig.tokenTtlSeconds(),
            queueDynamicConfig.rateLimitPerSecond(),
            queueDynamicConfig.batchSize(),
            queueDynamicConfig.paymentRateLimitPerSecond()
        ));
    }

    @PatchMapping("/config")
    public ApiResponse<QueueAdminV1Dto.ConfigResponse> updateConfig(
        @RequestBody QueueAdminV1Dto.ConfigUpdateRequest request
    ) {
        if (request.tokenTtlSeconds() != null) {
            queueDynamicConfig.updateTokenTtlSeconds(request.tokenTtlSeconds());
        }
        if (request.rateLimitPerSecond() != null) {
            queueDynamicConfig.updateRateLimitPerSecond(request.rateLimitPerSecond());
        }
        if (request.batchSize() != null) {
            queueDynamicConfig.updateBatchSize(request.batchSize());
        }
        if (request.paymentRateLimitPerSecond() != null) {
            queueDynamicConfig.updatePaymentRateLimitPerSecond(request.paymentRateLimitPerSecond());
        }
        return ApiResponse.success(new QueueAdminV1Dto.ConfigResponse(
            queueDynamicConfig.tokenTtlSeconds(),
            queueDynamicConfig.rateLimitPerSecond(),
            queueDynamicConfig.batchSize(),
            queueDynamicConfig.paymentRateLimitPerSecond()
        ));
    }
}
