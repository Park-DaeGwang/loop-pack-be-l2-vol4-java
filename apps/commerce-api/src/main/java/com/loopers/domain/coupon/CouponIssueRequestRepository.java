package com.loopers.domain.coupon;

import java.util.Optional;
import java.util.UUID;

public interface CouponIssueRequestRepository {
    CouponIssueRequestModel save(CouponIssueRequestModel request);
    Optional<CouponIssueRequestModel> find(UUID id);
}
