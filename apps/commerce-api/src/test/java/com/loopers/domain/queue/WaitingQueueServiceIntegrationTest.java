package com.loopers.domain.queue;

import com.loopers.domain.queue.QueueTokenScheduler;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
class WaitingQueueServiceIntegrationTest {

    @Autowired
    private WaitingQueueService waitingQueueService;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private QueueTokenScheduler queueTokenScheduler;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("redisQueue").reset();
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열 진입 시,")
    @Nested
    class Enter {

        @DisplayName("유효한 userId로 진입 시, 1-based 순번을 반환한다.")
        @Test
        void returnsPosition_whenValidUserId() {
            // arrange
            UUID userId = UUID.randomUUID();

            // act
            long position = waitingQueueService.enter(userId);

            // assert
            assertThat(position).isEqualTo(1L);
        }

        @DisplayName("여러 유저가 순서대로 진입 시, 진입 순서대로 순번이 부여된다.")
        @Test
        void assignsPositionInOrder_whenMultipleUsersEnter() {
            // arrange
            UUID firstUser = UUID.randomUUID();
            UUID secondUser = UUID.randomUUID();
            UUID thirdUser = UUID.randomUUID();

            // act
            long pos1 = waitingQueueService.enter(firstUser);
            long pos2 = waitingQueueService.enter(secondUser);
            long pos3 = waitingQueueService.enter(thirdUser);

            // assert
            assertAll(
                () -> assertThat(pos1).isEqualTo(1L),
                () -> assertThat(pos2).isEqualTo(2L),
                () -> assertThat(pos3).isEqualTo(3L)
            );
        }

        @DisplayName("이미 대기 중인 userId가 다시 진입 시, 기존 순번을 반환한다.")
        @Test
        void returnsExistingPosition_whenDuplicateEnter() {
            // arrange
            UUID userId = UUID.randomUUID();
            waitingQueueService.enter(userId);
            waitingQueueService.enter(UUID.randomUUID()); // 두 번째 유저

            // act
            long position = waitingQueueService.enter(userId); // 중복 진입

            // assert
            assertThat(position).isEqualTo(1L); // 순번 변화 없음
        }
    }

    @DisplayName("순번 조회 시,")
    @Nested
    class GetPosition {

        @DisplayName("대기 중인 유저 조회 시, 현재 순번과 예상 대기 시간을 반환한다.")
        @Test
        void returnsPositionAndWaitTime_whenUserIsWaiting() {
            // arrange
            UUID userId = UUID.randomUUID();
            waitingQueueService.enter(userId);

            // act
            QueuePositionResult result = waitingQueueService.getPosition(userId);

            // assert
            assertAll(
                () -> assertThat(result.position()).isEqualTo(1L),
                () -> assertThat(result.estimatedWaitSeconds()).isGreaterThanOrEqualTo(0L),
                () -> assertThat(result.token()).isEmpty()
            );
        }

        @DisplayName("대기열에 없는 userId 조회 시, NOT_FOUND 예외를 던진다.")
        @Test
        void throwsNotFound_whenUserNotInQueue() {
            // arrange
            UUID userId = UUID.randomUUID();

            // act & assert
            CoreException ex = assertThrows(CoreException.class,
                () -> waitingQueueService.getPosition(userId));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("입장 토큰이 발급된 유저 조회 시, 순번 0과 토큰을 반환한다.")
        @Test
        void returnsZeroPositionAndToken_whenTokenIssued() {
            // arrange
            UUID userId = UUID.randomUUID();
            waitingQueueService.issueToken(userId);

            // act
            QueuePositionResult result = waitingQueueService.getPosition(userId);

            // assert
            assertAll(
                () -> assertThat(result.position()).isEqualTo(0L),
                () -> assertThat(result.token()).isPresent()
            );
        }
    }

    @DisplayName("동시 진입 시,")
    @Nested
    class ConcurrentEnter {

        @DisplayName("모든 유저의 순번이 유일하게 보장된다.")
        @Test
        void assignsUniquePositions_whenConcurrentEnter() throws InterruptedException {
            // arrange
            int userCount = 10;
            List<Long> positions = new CopyOnWriteArrayList<>();
            ExecutorService executor = Executors.newFixedThreadPool(userCount);
            CountDownLatch latch = new CountDownLatch(userCount);

            // act
            for (int i = 0; i < userCount; i++) {
                executor.submit(() -> {
                    try {
                        long position = waitingQueueService.enter(UUID.randomUUID());
                        positions.add(position);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // assert
            assertAll(
                () -> assertThat(positions).hasSize(userCount),
                () -> assertThat(positions).doesNotHaveDuplicates(),
                () -> assertThat(positions).allMatch(p -> p >= 1 && p <= userCount)
            );
        }
    }

    @DisplayName("전체 대기 인원 조회 시,")
    @Nested
    class GetSize {

        @DisplayName("대기열에 진입한 인원 수를 반환한다.")
        @Test
        void returnsTotalWaitingCount() {
            // arrange
            waitingQueueService.enter(UUID.randomUUID());
            waitingQueueService.enter(UUID.randomUUID());
            waitingQueueService.enter(UUID.randomUUID());

            // act
            long size = waitingQueueService.getSize();

            // assert
            assertThat(size).isEqualTo(3L);
        }
    }
}
