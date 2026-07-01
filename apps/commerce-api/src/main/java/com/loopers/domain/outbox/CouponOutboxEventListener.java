package com.loopers.domain.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueRequestedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 발급 요청은 이 발행 자체가 유저 요청의 유일한 기록이라 유실되면 안 된다 — Outbox 패턴 실사용.
 * 비즈니스 데이터(발급 요청 row)와 발행 의도 기록이 원자적으로 묶여야 하므로
 * AFTER_COMMIT이 아닌 일반 @EventListener(같은 트랜잭션)를 쓴다.
 */
@RequiredArgsConstructor
@Component
public class CouponOutboxEventListener {

    private static final String COUPON_ISSUE_REQUESTS_TOPIC = "coupon-issue-requests";

    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    @EventListener
    public void handle(CouponIssueRequestedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventService.record(COUPON_ISSUE_REQUESTS_TOPIC, event.templateId().toString(), "CouponIssueRequestedEvent", payload);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "이벤트 직렬화에 실패했습니다.");
        }
    }
}
