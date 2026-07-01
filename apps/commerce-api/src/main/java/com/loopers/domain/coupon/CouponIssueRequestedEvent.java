package com.loopers.domain.coupon;

import java.util.UUID;

public record CouponIssueRequestedEvent(UUID eventId, UUID requestId, UUID userId, UUID templateId) {
    public CouponIssueRequestedEvent(UUID requestId, UUID userId, UUID templateId) {
        this(UUID.randomUUID(), requestId, userId, templateId);
    }
}
