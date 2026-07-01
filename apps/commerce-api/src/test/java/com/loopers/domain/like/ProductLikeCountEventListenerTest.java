package com.loopers.domain.like;

import com.loopers.config.CacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductLikeCountEventListenerTest {

    @DisplayName("handle")
    static class Handle {

        @DisplayName("ProductLikedEvent를 받으면, likeCount 증가 후 상품 캐시를 무효화한다.")
        @Test
        void incrementsThenEvictsCache_whenProductLikedEventReceived() {
            // arrange
            ProductLikeCountUpdater updater = mock(ProductLikeCountUpdater.class);
            CacheManager cacheManager = mock(CacheManager.class);
            Cache cache = mock(Cache.class);
            when(cacheManager.getCache(CacheConfig.PRODUCT_CACHE)).thenReturn(cache);
            ProductLikeCountEventListener listener = new ProductLikeCountEventListener(updater, cacheManager);
            UUID productId = UUID.randomUUID();

            // act
            listener.handle(new ProductLikedEvent(UUID.randomUUID(), productId));

            // assert
            verify(updater).increment(productId);
            verify(cache).evict(productId);
        }

        @DisplayName("ProductUnlikedEvent를 받으면, likeCount 감소 후 상품 캐시를 무효화한다.")
        @Test
        void decrementsThenEvictsCache_whenProductUnlikedEventReceived() {
            // arrange
            ProductLikeCountUpdater updater = mock(ProductLikeCountUpdater.class);
            CacheManager cacheManager = mock(CacheManager.class);
            Cache cache = mock(Cache.class);
            when(cacheManager.getCache(CacheConfig.PRODUCT_CACHE)).thenReturn(cache);
            ProductLikeCountEventListener listener = new ProductLikeCountEventListener(updater, cacheManager);
            UUID productId = UUID.randomUUID();

            // act
            listener.handle(new ProductUnlikedEvent(UUID.randomUUID(), productId));

            // assert
            verify(updater).decrement(productId);
            verify(cache).evict(productId);
        }
    }
}
