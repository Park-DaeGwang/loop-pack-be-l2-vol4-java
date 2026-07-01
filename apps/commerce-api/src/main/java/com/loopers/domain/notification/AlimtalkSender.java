package com.loopers.domain.notification;

import java.util.UUID;

public interface AlimtalkSender {
    void sendOrderCompleted(UUID userId, UUID orderId, Long amount);
}
