package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "coupon_issue_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueRequestModel extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponIssueStatus status;

    @Column(name = "user_coupon_id")
    private UUID userCouponId;

    @Column(name = "fail_reason")
    private String failReason;

    public CouponIssueRequestModel(UUID userId, UUID templateId) {
        this.userId = userId;
        this.templateId = templateId;
        this.status = CouponIssueStatus.PENDING;
    }

    public void succeed(UUID userCouponId) {
        this.status = CouponIssueStatus.SUCCESS;
        this.userCouponId = userCouponId;
    }

    public void fail(String reason) {
        this.status = CouponIssueStatus.FAILED;
        this.failReason = reason;
    }
}
