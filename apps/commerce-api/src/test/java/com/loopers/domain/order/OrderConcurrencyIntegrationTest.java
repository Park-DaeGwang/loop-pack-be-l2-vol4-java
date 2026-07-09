package com.loopers.domain.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemRequest;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.OrderFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
class OrderConcurrencyIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private StockService stockService;

    @Autowired
    private CouponTemplateService couponTemplateService;

    @Autowired
    private UserCouponService userCouponService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private UUID productId;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandService.create(BrandFixture.NAME, BrandFixture.DESCRIPTION);
        ProductModel product = productService.create(brand, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE);
        stockService.create(product.getId(), ProductFixture.INITIAL_QUANTITY);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private OrderInfo createOrder(UUID userId, UUID couponId) {
        return orderFacade.create(
            userId,
            List.of(new OrderItemRequest(productId, 1)),
            couponId,
            OrderFixture.RECEIVER_NAME, OrderFixture.RECEIVER_PHONE,
            OrderFixture.ZIP_CODE, OrderFixture.ADDRESS, OrderFixture.DETAIL_ADDRESS,
            UUID.randomUUID().toString()
        );
    }

    @DisplayName("동시성 — 동일 쿠폰 동시 주문")
    @Nested
    class ConcurrentCouponOrder {

        @DisplayName("동일 쿠폰으로 동시에 여러 주문을 시도해도, 쿠폰은 단 한번만 사용된다.")
        @Test
        void couponUsedOnce_underConcurrency() throws InterruptedException {
            // arrange
            UUID userId = UUID.randomUUID();
            CouponTemplateModel template = couponTemplateService.create(
                "테스트쿠폰", CouponType.FIXED, 3000L, null, ZonedDateTime.now().plusDays(30)
            );
            UUID couponId = userCouponService.issue(userId, template).getId();

            int threads = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger success = new AtomicInteger();
            AtomicInteger conflict = new AtomicInteger();

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        orderFacade.create(
                            userId,
                            List.of(new OrderItemRequest(productId, 1)),
                            couponId,
                            OrderFixture.RECEIVER_NAME, OrderFixture.RECEIVER_PHONE,
                            OrderFixture.ZIP_CODE, OrderFixture.ADDRESS, OrderFixture.DETAIL_ADDRESS,
                            UUID.randomUUID().toString()
                        );
                        success.incrementAndGet();
                    } catch (com.loopers.support.error.CoreException e) {
                        if (e.getErrorType() == com.loopers.support.error.ErrorType.CONFLICT) {
                            conflict.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            ready.await();
            start.countDown();
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);

            assertAll(
                () -> assertThat(success.get()).isEqualTo(1),
                () -> assertThat(conflict.get()).isEqualTo(threads - 1)
            );
        }
    }

    @DisplayName("동시성 — 동일 상품 동시 주문(재고 차감)")
    @Nested
    class ConcurrentStockOrder {

        @DisplayName("재고 5개 상품에 10명이 동시 주문해도, 정확히 5건만 성공하고 오버셀이 없다.")
        @Test
        void noOversell_underConcurrency() throws InterruptedException {
            // arrange
            int stock = 5;
            int threads = 10;
            BrandModel brand = brandService.create("테스트브랜드", "설명");
            ProductModel product = productService.create(brand, "재고테스트상품", "설명", 10000L);
            stockService.create(product.getId(), stock);
            UUID pid = product.getId();

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger success = new AtomicInteger();
            AtomicInteger conflict = new AtomicInteger();

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        orderFacade.create(
                            UUID.randomUUID(),
                            List.of(new OrderItemRequest(pid, 1)),
                            null,
                            OrderFixture.RECEIVER_NAME, OrderFixture.RECEIVER_PHONE,
                            OrderFixture.ZIP_CODE, OrderFixture.ADDRESS, OrderFixture.DETAIL_ADDRESS,
                            UUID.randomUUID().toString()
                        );
                        success.incrementAndGet();
                    } catch (com.loopers.support.error.CoreException e) {
                        if (e.getErrorType() == com.loopers.support.error.ErrorType.CONFLICT) {
                            conflict.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            ready.await();
            start.countDown();
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);

            assertAll(
                () -> assertThat(success.get()).isEqualTo(stock),
                () -> assertThat(conflict.get()).isEqualTo(threads - stock)
            );
        }
    }

    @DisplayName("동시성 — 중복 결제확정 콜백")
    @Nested
    class ConcurrentConfirm {

        @DisplayName("동일 주문에 confirm 콜백이 동시에 여러번 와도, 재고는 한번만 차감된다.")
        @Test
        void stockConfirmedOnce_underDuplicateCallback() throws InterruptedException {
            // arrange
            UUID userId = UUID.randomUUID();
            OrderInfo order = createOrder(userId, null);
            UUID orderId = order.id();
            Long amount = order.pgAmount();

            int threads = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        try {
                            paymentFacade.confirm(orderId, "pg-tx-dup", amount);
                        } catch (Exception ignored) {
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            ready.await();
            start.countDown();
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);

            // 재고: INITIAL_QUANTITY(10) - 1 = 9 (1개 예약 → confirm 1번만 차감)
            com.loopers.domain.stock.StockModel stock = stockService.getByProductId(productId);
            assertAll(
                () -> assertThat(stock.getTotalQuantity()).isEqualTo(ProductFixture.INITIAL_QUANTITY - 1),
                () -> assertThat(stock.getReservedQuantity()).isEqualTo(0)
            );
        }
    }
}
