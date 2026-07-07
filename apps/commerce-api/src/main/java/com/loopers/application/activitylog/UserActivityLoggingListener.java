package com.loopers.application.activitylog;

import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.order.OrderPlacedEvent;
import com.loopers.domain.product.ProductViewedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserActivityLoggingListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductViewedEvent event) {
        log.info("[활동로그] 상품조회 productId={}", event.productId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductLikedEvent event) {
        log.info("[활동로그] 좋아요 userId={} productId={}", event.userId(), event.productId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductUnlikedEvent event) {
        log.info("[활동로그] 좋아요취소 userId={} productId={}", event.userId(), event.productId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderPlacedEvent event) {
        log.info("[활동로그] 주문생성 userId={} orderId={}", event.userId(), event.orderId());
    }
}
