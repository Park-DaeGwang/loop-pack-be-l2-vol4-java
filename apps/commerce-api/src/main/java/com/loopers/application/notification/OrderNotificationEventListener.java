package com.loopers.application.notification;

import com.loopers.domain.notification.AlimtalkSender;
import com.loopers.domain.payment.PaymentConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderNotificationEventListener {

    private final AlimtalkSender alimtalkSender;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PaymentConfirmedEvent event) {
        alimtalkSender.sendOrderCompleted(event.userId(), event.orderId(), event.amount());
    }
}
