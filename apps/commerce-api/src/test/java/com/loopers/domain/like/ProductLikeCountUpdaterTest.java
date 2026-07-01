package com.loopers.domain.like;

import com.loopers.domain.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductLikeCountUpdaterTest {

    @DisplayName("increment/decrement")
    static class IncrementDecrement {

        @DisplayName("increment 호출 시, likeCount를 증가시키고 affected row 수를 반환한다.")
        @Test
        void incrementsLikeCount() {
            // arrange
            ProductRepository productRepository = mock(ProductRepository.class);
            UUID productId = UUID.randomUUID();
            when(productRepository.incrementLikeCount(productId)).thenReturn(1);
            ProductLikeCountUpdater updater = new ProductLikeCountUpdater(productRepository);

            // act
            int affected = updater.increment(productId);

            // assert
            assertThat(affected).isEqualTo(1);
            verify(productRepository).incrementLikeCount(productId);
        }

        @DisplayName("decrement 호출 시, likeCount를 감소시키고 affected row 수를 반환한다.")
        @Test
        void decrementsLikeCount() {
            // arrange
            ProductRepository productRepository = mock(ProductRepository.class);
            UUID productId = UUID.randomUUID();
            when(productRepository.decrementLikeCount(productId)).thenReturn(1);
            ProductLikeCountUpdater updater = new ProductLikeCountUpdater(productRepository);

            // act
            int affected = updater.decrement(productId);

            // assert
            assertThat(affected).isEqualTo(1);
            verify(productRepository).decrementLikeCount(productId);
        }
    }
}
