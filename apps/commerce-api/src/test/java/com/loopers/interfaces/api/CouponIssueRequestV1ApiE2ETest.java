package com.loopers.interfaces.api;

import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.fixture.UserFixture;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponIssueRequestV1ApiE2ETest {

    private static final String USERS_URL = "/api/v1/users";
    private static final String ADMIN_COUPONS_URL = "/api-admin/v1/coupons";
    private static final LocalDateTime EXPIRED_AT = LocalDateTime.now().plusDays(30);

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private HttpHeaders userAuthHeaders(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", UserFixture.PASSWORD);
        return headers;
    }

    private void registerUser(String loginId) {
        testRestTemplate.exchange(
            USERS_URL, HttpMethod.POST,
            new HttpEntity<>(new UserV1Dto.RegisterRequest(loginId, UserFixture.PASSWORD, UserFixture.NAME,
                UserFixture.BIRTH, loginId + "@loopers.com")),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {}
        );
    }

    private UUID createLimitedTemplate(String name, long totalQuantity) {
        ResponseEntity<ApiResponse<CouponV1Dto.TemplateResponse>> response = testRestTemplate.exchange(
            ADMIN_COUPONS_URL, HttpMethod.POST,
            new HttpEntity<>(new CouponV1Dto.CreateRequest(name, CouponType.RATE, 10L, null, EXPIRED_AT, totalQuantity), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private String requestIssueUrl(UUID templateId) {
        return "/api/v1/coupons/" + templateId + "/issue-requests";
    }

    private String statusUrl(UUID requestId) {
        return "/api/v1/coupons/issue-requests/" + requestId;
    }

    private CouponV1Dto.IssueRequestResponse pollUntilTerminal(UUID requestId, HttpHeaders headers) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        CouponV1Dto.IssueRequestResponse last = null;
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<ApiResponse<CouponV1Dto.IssueRequestResponse>> response = testRestTemplate.exchange(
                statusUrl(requestId), HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );
            last = response.getBody().data();
            if (last.status() != CouponIssueStatus.PENDING) {
                return last;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("발급 요청이 제한 시간 내에 처리되지 않았다 — requestId=" + requestId + ", last=" + last);
    }

    @DisplayName("선착순 쿠폰 발급 동시성")
    @Nested
    class Concurrency {

        @DisplayName("N명이 동시에 요청해도, 정확히 totalQuantity만큼만 SUCCESS하고 나머지는 매진으로 FAILED된다.")
        @Test
        void issuesExactlyTotalQuantity_whenConcurrentRequests() throws InterruptedException {
            // arrange
            long totalQuantity = 5;
            int users = 10;
            UUID templateId = createLimitedTemplate("선착순쿠폰", totalQuantity);

            List<String> loginIds = new ArrayList<>();
            for (int i = 0; i < users; i++) {
                String loginId = "racer" + i;
                registerUser(loginId);
                loginIds.add(loginId);
            }

            ExecutorService pool = Executors.newFixedThreadPool(users);
            CountDownLatch ready = new CountDownLatch(users);
            CountDownLatch start = new CountDownLatch(1);
            ConcurrentHashMap<String, UUID> requestIdsByUser = new ConcurrentHashMap<>();

            // act — 동시에 발급 요청 (API 자체는 즉시 응답, 실제 경합은 컨슈머 처리 단계에서 발생)
            for (String loginId : loginIds) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        ResponseEntity<ApiResponse<CouponV1Dto.IssueRequestResponse>> response = testRestTemplate.exchange(
                            requestIssueUrl(templateId), HttpMethod.POST,
                            new HttpEntity<>(userAuthHeaders(loginId)),
                            new ParameterizedTypeReference<>() {}
                        );
                        requestIdsByUser.put(loginId, response.getBody().data().requestId());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            ready.await();
            start.countDown();
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);

            assertThat(requestIdsByUser).hasSize(users);

            // 각 요청의 최종 상태 폴링
            long successCount = 0;
            long failedCount = 0;
            for (String loginId : loginIds) {
                CouponV1Dto.IssueRequestResponse result = pollUntilTerminal(requestIdsByUser.get(loginId), userAuthHeaders(loginId));
                if (result.status() == CouponIssueStatus.SUCCESS) {
                    successCount++;
                } else if (result.status() == CouponIssueStatus.FAILED) {
                    failedCount++;
                    assertThat(result.failReason()).isEqualTo("매진되었습니다.");
                }
            }

            // assert
            assertThat(successCount).isEqualTo(totalQuantity);
            assertThat(failedCount).isEqualTo(users - totalQuantity);
        }
    }
}
