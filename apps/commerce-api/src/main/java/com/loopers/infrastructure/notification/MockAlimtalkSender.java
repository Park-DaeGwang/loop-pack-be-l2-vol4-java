package com.loopers.infrastructure.notification;

import com.loopers.domain.notification.AlimtalkSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class MockAlimtalkSender implements AlimtalkSender {

    @Override
    public void sendOrderCompleted(UUID userId, UUID orderId, Long amount) {
        // 실제 카카오 알림톡 API 연동 전 mock — 발송 로그로 대체
        log.info("[알림톡 발송] userId={}, orderId={}, amount={} - 주문이 완료되었습니다.", userId, orderId, amount);
    }
}
