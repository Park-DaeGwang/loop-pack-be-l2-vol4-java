package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateModel, UUID> {
    Optional<CouponTemplateModel> findByIdAndDeletedAtIsNull(UUID id);

    // 선착순 발급 원자적 카운트 증가 — issuedCount < totalQuantity일 때만 성공(affected=1)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CouponTemplateModel c SET c.issuedCount = c.issuedCount + 1 " +
           "WHERE c.id = :id AND c.issuedCount < c.totalQuantity")
    int tryIssue(@Param("id") UUID id);
}
