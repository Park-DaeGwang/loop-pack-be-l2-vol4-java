package com.loopers.application.notification;

import com.loopers.domain.notification.AlimtalkSender;
import com.loopers.domain.payment.PaymentConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderNotificationEventListenerTest {

    @DisplayName("handle")
    static class Handle {

        @DisplayName("PaymentConfirmedEvent를 받으면, 이벤트의 userId/orderId/amount로 알림톡을 발송한다.")
        @Test
        void sendsAlimtalk_whenPaymentConfirmedEventReceived() {
            // arrange
            AlimtalkSender alimtalkSender = mock(AlimtalkSender.class);
            OrderNotificationEventListener listener = new OrderNotificationEventListener(alimtalkSender);
            UUID orderId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            PaymentConfirmedEvent event = new PaymentConfirmedEvent(orderId, userId, 10000L, List.of());

            // act
            listener.handle(event);

            // assert
            verify(alimtalkSender).sendOrderCompleted(userId, orderId, 10000L);
        }
    }
}
