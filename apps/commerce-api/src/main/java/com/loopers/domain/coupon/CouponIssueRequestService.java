package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
public class CouponIssueRequestService {

    private final CouponIssueRequestRepository couponIssueRequestRepository;

    public CouponIssueRequestModel create(UUID userId, UUID templateId) {
        return couponIssueRequestRepository.save(new CouponIssueRequestModel(userId, templateId));
    }

    public CouponIssueRequestModel get(UUID requestId) {
        return couponIssueRequestRepository.find(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + requestId + "] 발급 요청을 찾을 수 없습니다."));
    }

    /** 소유권 검증 — 본인 요청 아니면 NOT_FOUND */
    public CouponIssueRequestModel getOwned(UUID requestId, UUID userId) {
        CouponIssueRequestModel request = get(requestId);
        if (!request.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다.");
        }
        return request;
    }
}
