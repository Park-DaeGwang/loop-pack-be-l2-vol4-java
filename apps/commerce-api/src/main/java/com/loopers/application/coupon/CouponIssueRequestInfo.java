package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueStatus;

import java.util.UUID;

public record CouponIssueRequestInfo(
    UUID requestId,
    CouponIssueStatus status,
    UUID userCouponId,
    String failReason
) {
    public static CouponIssueRequestInfo from(CouponIssueRequestModel model) {
        return new CouponIssueRequestInfo(model.getId(), model.getStatus(), model.getUserCouponId(), model.getFailReason());
    }
}
