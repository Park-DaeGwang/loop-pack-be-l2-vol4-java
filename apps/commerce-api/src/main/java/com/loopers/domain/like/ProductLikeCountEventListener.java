package com.loopers.domain.like;

import com.loopers.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductLikeCountEventListener {

    private final ProductLikeCountUpdater productLikeCountUpdater;
    private final CacheManager cacheManager;

    @Async("likeCountExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductLikedEvent event) {
        int affected = productLikeCountUpdater.increment(event.productId());
        if (affected != 1) {
            log.warn("좋아요 카운트 증가 실패 — productId={}, affected={}", event.productId(), affected);
        }
        evictProductCache(event.productId());
    }

    @Async("likeCountExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductUnlikedEvent event) {
        int affected = productLikeCountUpdater.decrement(event.productId());
        if (affected != 1) {
            log.warn("좋아요 카운트 감소 실패 — productId={}, affected={}", event.productId(), affected);
        }
        evictProductCache(event.productId());
    }

    private void evictProductCache(UUID productId) {
        cacheManager.getCache(CacheConfig.PRODUCT_CACHE).evict(productId);
    }
}
