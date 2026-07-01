package com.loopers.domain.coupon;

import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * coupon-issue-requests 컨슈머의 실제 발급 처리 로직.
 * key=templateId 파티셔닝으로 같은 쿠폰(+같은 유저)의 요청은 항상 순차 처리되므로,
 * 중복발급 사전 체크(existsByUserIdAndTemplateId) 이후 실제 insert 시점에 경합이 생기지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueProcessingService {

    private final EventHandledRepository eventHandledRepository;
    private final CouponIssueRequestService couponIssueRequestService;
    private final CouponTemplateService couponTemplateService;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserCouponService userCouponService;

    @Transactional
    public void process(UUID eventId, UUID requestId, UUID userId, UUID templateId) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            log.info("이미 처리된 이벤트 — eventId={}", eventId);
            return;
        }

        CouponIssueRequestModel request = couponIssueRequestService.get(requestId);
        if (request.getStatus() != CouponIssueStatus.PENDING) {
            eventHandledRepository.save(new EventHandledModel(eventId));
            return;
        }

        if (userCouponRepository.existsByUserIdAndTemplateId(userId, templateId)) {
            request.fail("이미 발급받은 쿠폰입니다.");
            eventHandledRepository.save(new EventHandledModel(eventId));
            return;
        }

        int affected = couponTemplateRepository.tryIssue(templateId);
        // tryIssue의 clearAutomatically=true가 1LC를 비워 request가 detach됨 — 재조회 필요
        request = couponIssueRequestService.get(requestId);
        if (affected == 0) {
            request.fail("매진되었습니다.");
            eventHandledRepository.save(new EventHandledModel(eventId));
            return;
        }

        CouponTemplateModel template = couponTemplateService.get(templateId);
        UserCouponModel userCoupon = userCouponService.issue(userId, template);
        request.succeed(userCoupon.getId());
        eventHandledRepository.save(new EventHandledModel(eventId));
    }
}
