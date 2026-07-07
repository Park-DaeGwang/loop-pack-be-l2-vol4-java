package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestModel, UUID> {
}
