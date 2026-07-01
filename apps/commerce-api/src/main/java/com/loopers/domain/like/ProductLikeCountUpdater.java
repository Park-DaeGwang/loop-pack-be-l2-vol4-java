package com.loopers.domain.like;

import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * likeCount 실갱신(DB) 전용 — 원인 조사용 임시 컴포넌트.
 * @TransactionalEventListener 메서드엔 @Transactional을 못 붙이므로 별도 빈으로 분리해서 위임하는 구조를 검증한다.
 */
@Component
@RequiredArgsConstructor
public class ProductLikeCountUpdater {

    private final ProductRepository productRepository;

    @Transactional
    public int increment(UUID productId) {
        return productRepository.incrementLikeCount(productId);
    }

    @Transactional
    public int decrement(UUID productId) {
        return productRepository.decrementLikeCount(productId);
    }
}
