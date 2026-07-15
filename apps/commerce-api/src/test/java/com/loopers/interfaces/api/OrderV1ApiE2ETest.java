package com.loopers.interfaces.api;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.fixture.UserFixture;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String USERS_URL    = "/api/v1/users";
    private static final String BRANDS_URL   = "/api-admin/v1/brands";
    private static final String PRODUCTS_URL = "/api-admin/v1/products";
    private static final String ORDERS_URL            = "/api/v1/orders";
    private static final String PAYMENTS_CALLBACK_URL = "/api/v1/payments/callback";
    private static final String ADMIN_COUPONS_URL     = "/api-admin/v1/coupons";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private WaitingQueueService waitingQueueService;

    private UUID userId;
    private UUID productId;
    private UUID brandId;

    @BeforeEach
    void setUp() {
        ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> userResp = testRestTemplate.exchange(
            USERS_URL, HttpMethod.POST,
            new HttpEntity<>(UserFixture.createRequest()),
            new ParameterizedTypeReference<>() {}
        );
        assertThat(userResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        userId = userResp.getBody().data().id();

        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> brandResp = testRestTemplate.exchange(
            BRANDS_URL, HttpMethod.POST,
            new HttpEntity<>(new BrandV1Dto.CreateRequest(BrandFixture.NAME, BrandFixture.DESCRIPTION), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        brandId = brandResp.getBody().data().id();

        ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> productResp = testRestTemplate.exchange(
            PRODUCTS_URL, HttpMethod.POST,
            new HttpEntity<>(new ProductV1Dto.CreateRequest(
                brandId, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE, ProductFixture.INITIAL_QUANTITY
            ), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        productId = productResp.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", UserFixture.LOGIN_ID);
        headers.set("X-Loopers-LoginPw", UserFixture.PASSWORD);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    /** 주문 생성용 — 매 호출마다 토큰 재발급 + 새 Idempotency-Key */
    private HttpHeaders orderHeaders() {
        waitingQueueService.issueToken(userId);
        HttpHeaders headers = authHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        headers.set("X-Queue-Token", waitingQueueService.findToken(userId).orElseThrow());
        return headers;
    }

    /** userId 지정 주문 헤더 — 동시성 테스트에서 복수 유저 시나리오용 */
    private HttpHeaders orderHeaders(UUID uid, String loginId, String loginPw) {
        waitingQueueService.issueToken(uid);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", loginPw);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        headers.set("X-Queue-Token", waitingQueueService.findToken(uid).orElseThrow());
        return headers;
    }

    private void postCallback(UUID orderId, Long amount, String status) {
        testRestTemplate.exchange(
            PAYMENTS_CALLBACK_URL, HttpMethod.POST,
            new HttpEntity<>(new PaymentV1Dto.CallbackPayload(
                "pg-tx-001", orderId.toString(), "SAMSUNG", "1234-5678-9814-1451", amount, status, null
            )),
            new ParameterizedTypeReference<Void>() {}
        );
    }

    private OrderV1Dto.CreateRequest validCreateRequest() {
        OrderV1Dto.ShippingInfoRequest shipping = new OrderV1Dto.ShippingInfoRequest(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구 테헤란로 1", "101호"
        );
        return new OrderV1Dto.CreateRequest(shipping, List.of(new OrderV1Dto.OrderItemRequest(productId, 2)));
    }

    /** 쿠폰 템플릿 생성(admin) → templateId */
    private UUID createCouponTemplate(CouponType type, long value, Long minOrderAmount) {
        ResponseEntity<ApiResponse<CouponV1Dto.TemplateResponse>> resp = testRestTemplate.exchange(
            ADMIN_COUPONS_URL, HttpMethod.POST,
            new HttpEntity<>(new CouponV1Dto.CreateRequest("쿠폰" + UUID.randomUUID(), type, value, minOrderAmount,
                LocalDateTime.now().plusDays(30)), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return resp.getBody().data().id();
    }

    /** 본인에게 발급 → userCouponId */
    private UUID issueCoupon(UUID templateId) {
        ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> resp = testRestTemplate.exchange(
            "/api/v1/coupons/" + templateId + "/issue", HttpMethod.POST,
            new HttpEntity<>(authHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return resp.getBody().data().id();
    }

    private OrderV1Dto.CreateRequest createRequestWithCoupon(int quantity, UUID couponId) {
        OrderV1Dto.ShippingInfoRequest shipping = new OrderV1Dto.ShippingInfoRequest(
            "홍길동", "010-1234-5678", "12345", "서울시", "101호"
        );
        return new OrderV1Dto.CreateRequest(shipping, List.of(new OrderV1Dto.OrderItemRequest(productId, quantity)), couponId);
    }


    /** 지정 재고로 상품 생성(admin) → productId */
    private UUID createProduct(int quantity) {
        ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> resp = testRestTemplate.exchange(
            PRODUCTS_URL, HttpMethod.POST,
            new HttpEntity<>(new ProductV1Dto.CreateRequest(
                brandId, "상품" + UUID.randomUUID(), ProductFixture.DESCRIPTION, ProductFixture.PRICE, quantity
            ), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return resp.getBody().data().id();
    }

    private OrderV1Dto.CreateRequest orderRequest(UUID pid, int quantity) {
        OrderV1Dto.ShippingInfoRequest shipping = new OrderV1Dto.ShippingInfoRequest(
            "홍길동", "010-1234-5678", "12345", "서울시", "101호"
        );
        return new OrderV1Dto.CreateRequest(shipping, List.of(new OrderV1Dto.OrderItemRequest(pid, quantity)), null);
    }

    @DisplayName("POST /api/v1/orders — 쿠폰 적용")
    @Nested
    class CreateOrderWithCoupon {

        @DisplayName("정액 쿠폰 적용 시, 200 + 할인액·최종금액이 반영된다.")
        @Test
        void appliesFixedCoupon() {
            UUID templateId = createCouponTemplate(CouponType.FIXED, 3000L, null);
            UUID couponId = issueCoupon(templateId);
            long original = ProductFixture.PRICE * 2;

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().originalAmount()).isEqualTo(original),
                () -> assertThat(response.getBody().data().discountAmount()).isEqualTo(3000L),
                () -> assertThat(response.getBody().data().pgAmount()).isEqualTo(original - 3000L),
                () -> assertThat(response.getBody().data().couponId()).isEqualTo(couponId)
            );
        }

        @DisplayName("존재하지 않는 쿠폰으로 주문 시, 404 를 반환한다.")
        @Test
        void returnsNotFound_whenCouponNotExists() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, UUID.randomUUID()), orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("이미 사용된 쿠폰으로 재주문 시, 409 를 반환한다.")
        @Test
        void returnsConflict_whenCouponAlreadyUsed() {
            UUID templateId = createCouponTemplate(CouponType.FIXED, 3000L, null);
            UUID couponId = issueCoupon(templateId);
            testRestTemplate.exchange(ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), orderHeaders()),
                new ParameterizedTypeReference<Void>() {});

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("최소 주문 금액 미달 쿠폰으로 주문 시, 409 를 반환한다.")
        @Test
        void returnsConflict_whenBelowMinOrderAmount() {
            long original = ProductFixture.PRICE * 2;
            UUID templateId = createCouponTemplate(CouponType.FIXED, 3000L, original + 1);
            UUID couponId = issueCoupon(templateId);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("결제 실패 시, 쿠폰이 복구되어 동일 쿠폰으로 재주문할 수 있다.")
        @Test
        void releasesCoupon_whenPaymentFails() {
            UUID templateId = createCouponTemplate(CouponType.FIXED, 3000L, null);
            UUID couponId = issueCoupon(templateId);

            // 1차 주문 — 쿠폰 사용
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            Long amount = created.getBody().data().pgAmount();

            // 결제 실패 콜백 → 쿠폰 복구
            postCallback(orderId, amount, "FAILED");

            // 같은 쿠폰으로 재주문 → 성공 (복구 확인)
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> reorder = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(reorder.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("쿠폰 적용 주문을 취소하면, 쿠폰이 복구되어 재사용 가능하다.")
        @Test
        void releasesCoupon_whenCancelled() {
            UUID templateId = createCouponTemplate(CouponType.FIXED, 3000L, null);
            UUID couponId = issueCoupon(templateId);

            // 주문(쿠폰 사용) → 결제 확정(CONFIRMED)
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            Long amount = created.getBody().data().pgAmount();
            postCallback(orderId, amount, "SUCCESS");

            // 주문 취소 → 쿠폰 복구
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> cancelled = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<>() {}
            );
            assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 같은 쿠폰으로 재주문 → 성공 (복구 확인)
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> reorder = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(reorder.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("POST /api/v1/orders — 주문 생성")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 요청이면, 200 + PENDING 주문을 반환한다.")
        @Test
        void returnsOrder_whenValid() {
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().status().name()).isEqualTo("PENDING"),
                () -> assertThat(response.getBody().data().pgAmount()).isEqualTo(ProductFixture.PRICE * 2),
                () -> assertThat(response.getBody().data().items()).hasSize(1)
            );
        }

        @DisplayName("동일 멱등 키로 재요청 시, 새 주문 생성 없이 기존 주문을 반환한다.")
        @Test
        void returnsExistingOrder_whenSameIdempotencyKey() {
            String idempotencyKey = UUID.randomUUID().toString();

            waitingQueueService.issueToken(userId);
            String token = waitingQueueService.findToken(userId).orElseThrow();

            HttpHeaders firstHeaders = authHeaders();
            firstHeaders.set("Idempotency-Key", idempotencyKey);
            firstHeaders.set("X-Queue-Token", token);

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> first = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), firstHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // 첫 주문 성공 후 토큰 소진 → 재발급 후 동일 멱등 키로 재시도
            waitingQueueService.issueToken(userId);
            String token2 = waitingQueueService.findToken(userId).orElseThrow();

            HttpHeaders secondHeaders = authHeaders();
            secondHeaders.set("Idempotency-Key", idempotencyKey);
            secondHeaders.set("X-Queue-Token", token2);

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> second = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), secondHeaders),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(second.getBody().data().id()).isEqualTo(first.getBody().data().id())
            );
        }

        @DisplayName("인증 헤더 없이 요청 시, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenNoAuth() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("재고가 부족하면, 409를 반환한다.")
        @Test
        void returnsConflict_whenStockInsufficient() {
            OrderV1Dto.CreateRequest req = new OrderV1Dto.CreateRequest(
                new OrderV1Dto.ShippingInfoRequest("홍길동", "010-1234-5678", "12345", "서울시", null),
                List.of(new OrderV1Dto.OrderItemRequest(productId, ProductFixture.INITIAL_QUANTITY + 1))
            );

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(req, orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("존재하지 않는 상품이면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            OrderV1Dto.CreateRequest req = new OrderV1Dto.CreateRequest(
                new OrderV1Dto.ShippingInfoRequest("홍길동", "010-1234-5678", "12345", "서울시", null),
                List.of(new OrderV1Dto.OrderItemRequest(UUID.randomUUID(), 1))
            );

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(req, orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId} — 주문 단건 조회")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문 조회 시, 200 + 주문 정보를 반환한다.")
        @Test
        void returnsOrder_whenOwner() {
            // arrange
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(orderId)
            );
        }

        @DisplayName("존재하지 않는 주문 ID면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenNotExists() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + UUID.randomUUID(), HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/orders — 주문 목록 조회")
    @Nested
    class GetOrderList {

        @DisplayName("본인 주문 목록 조회 시, 200 + 목록을 반환한다.")
        @Test
        void returnsList_whenOwnUser() {
            // arrange
            testRestTemplate.exchange(ORDERS_URL, HttpMethod.POST, new HttpEntity<>(validCreateRequest(), orderHeaders()), new ParameterizedTypeReference<>() {});

            String startAt = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String endAt   = ZonedDateTime.now(ZoneOffset.UTC).plusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String url = ORDERS_URL + "?userId=" + userId + "&startAt=" + startAt + "&endAt=" + endAt + "&page=0&size=10";

            // act
            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderResponse>>> response = testRestTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(1)
            );
        }

        @DisplayName("타인의 주문 목록 조회 시, 404를 반환한다.")
        @Test
        void returnsNotFound_whenOtherUser() {
            String startAt = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String endAt   = ZonedDateTime.now(ZoneOffset.UTC).plusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String url = ORDERS_URL + "?userId=" + UUID.randomUUID() + "&startAt=" + startAt + "&endAt=" + endAt + "&page=0&size=10";

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("POST /api/v1/orders/{orderId}/cancel — 주문 취소")
    @Nested
    class CancelOrder {

        @DisplayName("CONFIRMED 주문 취소 시, 200 + CANCELLED 상태를 반환한다.")
        @Test
        void cancelsOrder_whenConfirmed() {
            // arrange — 주문 생성 → 결제 확정
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            Long amount  = created.getBody().data().pgAmount();

            postCallback(orderId, amount, "SUCCESS");

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().status().name()).isEqualTo("CANCELLED")
            );
        }

        @DisplayName("PENDING 주문 취소 시, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenPending() {
            // arrange
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), orderHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

}
