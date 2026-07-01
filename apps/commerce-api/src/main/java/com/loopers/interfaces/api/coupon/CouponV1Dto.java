package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

public class CouponV1Dto {

    public record CreateRequest(
        @NotBlank String name,
        @NotNull CouponType type,
        @NotNull Long value,
        Long minOrderAmount,
        @NotNull LocalDateTime expiredAt,
        Long totalQuantity // null이면 무제한, 값이 있으면 선착순(수량 제한) 쿠폰
    ) {
        public CreateRequest(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
            this(name, type, value, minOrderAmount, expiredAt, null);
        }
    }

    public record UpdateRequest(
        @NotBlank String name,
        @NotNull CouponType type,
        @NotNull Long value,
        Long minOrderAmount,
        @NotNull LocalDateTime expiredAt
    ) {}

    public record TemplateResponse(
        UUID id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime createdAt,
        ZonedDateTime deletedAt,
        Long totalQuantity,
        long issuedCount
    ) {
        public static TemplateResponse from(CouponInfo info) {
            return new TemplateResponse(
                info.id(),
                info.name(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.createdAt(),
                info.deletedAt(),
                info.totalQuantity(),
                info.issuedCount()
            );
        }
    }

    public record IssueRequestResponse(
        UUID requestId,
        CouponIssueStatus status,
        UUID userCouponId,
        String failReason
    ) {
        public static IssueRequestResponse from(CouponIssueRequestInfo info) {
            return new IssueRequestResponse(info.requestId(), info.status(), info.userCouponId(), info.failReason());
        }
    }

    public record UserCouponResponse(
        UUID id,
        UUID templateId,
        UserCouponStatus status,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt,
        UUID orderId,
        ZonedDateTime usedAt,
        ZonedDateTime issuedAt
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                info.id(),
                info.templateId(),
                info.status(),
                info.type(),
                info.value(),
                info.minOrderAmount(),
                info.expiredAt(),
                info.orderId(),
                info.usedAt(),
                info.issuedAt()
            );
        }
    }
}
