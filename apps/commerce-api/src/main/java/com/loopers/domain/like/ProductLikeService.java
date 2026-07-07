package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Like + Product 크로스 애그리거트 도메인 서비스.
 *
 * 좋아요/취소는 그 자체로 완결된 사실이고, Product.likeCount는 그 파생 집계값이다.
 * 집계 갱신 실패가 좋아요 자체를 롤백시키지 않도록 이벤트로 분리한다(eventual consistency).
 */
@RequiredArgsConstructor
@Component
public class ProductLikeService {

    private final LikeService likeService;
    private final ApplicationEventPublisher eventPublisher;

    /** 좋아요 — 멱등 삽입이 실제로 새로 넣었을 때만 이벤트 발행 */
    public void like(UUID userId, UUID productId) {
        if (likeService.like(userId, productId)) {
            eventPublisher.publishEvent(new ProductLikedEvent(userId, productId));
        }
    }

    /** 좋아요 취소 — 실제로 삭제됐을 때만 이벤트 발행 */
    public void unlike(UUID userId, UUID productId) {
        if (likeService.unlike(userId, productId)) {
            eventPublisher.publishEvent(new ProductUnlikedEvent(userId, productId));
        }
    }
}
