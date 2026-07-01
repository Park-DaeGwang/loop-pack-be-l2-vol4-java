package com.loopers.application.activitylog;

import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.order.OrderPlacedEvent;
import com.loopers.domain.product.ProductViewedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class UserActivityLoggingListenerTest {

    private final UserActivityLoggingListener listener = new UserActivityLoggingListener();

    @DisplayName("ProductViewedEvent를 받으면, 예외 없이 로깅된다.")
    @Test
    void doesNotThrow_whenProductViewedEventReceived() {
        assertThatCode(() -> listener.handle(new ProductViewedEvent(UUID.randomUUID())))
            .doesNotThrowAnyException();
    }

    @DisplayName("ProductLikedEvent를 받으면, 예외 없이 로깅된다.")
    @Test
    void doesNotThrow_whenProductLikedEventReceived() {
        assertThatCode(() -> listener.handle(new ProductLikedEvent(UUID.randomUUID(), UUID.randomUUID())))
            .doesNotThrowAnyException();
    }

    @DisplayName("ProductUnlikedEvent를 받으면, 예외 없이 로깅된다.")
    @Test
    void doesNotThrow_whenProductUnlikedEventReceived() {
        assertThatCode(() -> listener.handle(new ProductUnlikedEvent(UUID.randomUUID(), UUID.randomUUID())))
            .doesNotThrowAnyException();
    }

    @DisplayName("OrderPlacedEvent를 받으면, 예외 없이 로깅된다.")
    @Test
    void doesNotThrow_whenOrderPlacedEventReceived() {
        assertThatCode(() -> listener.handle(new OrderPlacedEvent(UUID.randomUUID(), UUID.randomUUID())))
            .doesNotThrowAnyException();
    }
}
