package com.loopers.domain.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductLikeCountUpdater 동시성 회귀 테스트 — 이벤트/@Async 경로와 분리해서
 * 순수 JPA @Transactional 원자적 UPDATE 레이어 자체의 정합성을 검증한다.
 */
@SpringBootTest
class ProductLikeCountUpdaterConcurrencyTest {

    @Autowired
    private ProductLikeCountUpdater productLikeCountUpdater;

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("increment를 N개 스레드에서 동시 호출하면, likeCount는 정확히 N이 되고 모든 호출의 affected는 1이다.")
    @Test
    void noLostUpdate_whenConcurrentIncrement() throws InterruptedException {
        // arrange
        BrandModel brand = brandService.create(BrandFixture.NAME, BrandFixture.DESCRIPTION);
        ProductModel product = productService.create(brand, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE);
        int threads = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Integer> affectedResults = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    int affected = productLikeCountUpdater.increment(product.getId());
                    affectedResults.add(affected);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        // act
        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        // assert
        ProductModel result = productService.get(product.getId());
        assertThat(affectedResults).hasSize(threads);
        assertThat(affectedResults).allMatch(affected -> affected == 1);
        assertThat(result.getLikeCount()).isEqualTo(threads);
    }
}
