package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestService;
import com.loopers.domain.coupon.CouponIssueRequestedEvent;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponTemplateService couponTemplateService;
    private final UserCouponService userCouponService;
    private final CouponIssueRequestService couponIssueRequestService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CouponInfo create(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt, Long totalQuantity) {
        return CouponInfo.from(couponTemplateService.create(name, type, value, minOrderAmount, toZoned(expiredAt), totalQuantity));
    }

    /** 어드민용 — 삭제된 템플릿 포함 */
    public CouponInfo get(UUID id) {
        return CouponInfo.from(couponTemplateService.get(id));
    }

    /** 어드민 목록 — 삭제된 템플릿 포함, 페이징 */
    public Page<CouponInfo> getList(Pageable pageable) {
        return couponTemplateService.getList(pageable).map(CouponInfo::from);
    }

    @Transactional
    public CouponInfo update(UUID id, String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        return CouponInfo.from(couponTemplateService.update(id, name, type, value, minOrderAmount, toZoned(expiredAt)));
    }

    @Transactional
    public void delete(UUID id) {
        couponTemplateService.delete(id);
    }

    /** 쿠폰 발급 — 활성 템플릿 조회 후 발급. 만료 템플릿이면 거부. 선착순(수량 제한) 템플릿은 비동기 발급만 허용. */
    @Transactional
    public UserCouponInfo issue(UUID userId, UUID templateId) {
        CouponTemplateModel template = couponTemplateService.getActive(templateId);
        if (template.isLimited()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선착순(수량 제한) 쿠폰은 비동기 발급 API(POST .../issue-requests)를 사용하세요.");
        }
        ZonedDateTime now = ZonedDateTime.now();
        if (template.isExpired(now)) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        return UserCouponInfo.from(userCouponService.issue(userId, template), now);
    }

    /** 선착순 쿠폰 발급 요청 — 비동기. 요청만 접수하고 즉시 반환, 실제 발급은 컨슈머가 처리. */
    @Transactional
    public CouponIssueRequestInfo requestIssue(UUID userId, UUID templateId) {
        CouponTemplateModel template = couponTemplateService.getActive(templateId);
        if (!template.isLimited()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "선착순(수량 제한) 쿠폰만 비동기 발급을 지원합니다.");
        }
        if (template.isExpired(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        CouponIssueRequestModel request = couponIssueRequestService.create(userId, templateId);
        eventPublisher.publishEvent(new CouponIssueRequestedEvent(request.getId(), userId, templateId));
        return CouponIssueRequestInfo.from(request);
    }

    /** 발급 요청 상태 조회 — 폴링용 */
    public CouponIssueRequestInfo getIssueRequestStatus(UUID requestId, UUID userId) {
        return CouponIssueRequestInfo.from(couponIssueRequestService.getOwned(requestId, userId));
    }

    /** 내 쿠폰 목록 — 상태는 조회 시점 기준 동적 판정 */
    public List<UserCouponInfo> getMyCoupons(UUID userId) {
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponService.getMyCoupons(userId).stream()
            .map(c -> UserCouponInfo.from(c, now))
            .toList();
    }

    /** 어드민 — 템플릿별 발급 내역 */
    public Page<UserCouponInfo> getIssuesByTemplate(UUID templateId, Pageable pageable) {
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponService.getIssuesByTemplate(templateId, pageable).map(c -> UserCouponInfo.from(c, now));
    }

    private ZonedDateTime toZoned(LocalDateTime expiredAt) {
        return expiredAt.atZone(ZoneId.systemDefault());
    }
}
