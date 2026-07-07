package com.loopers.interfaces.api;

import com.loopers.fixture.UserFixture;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.queue.QueueV1Dto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueV1ApiE2ETest {

    private static final String ENDPOINT_ENTER    = "/api/v1/queue/enter";
    private static final String ENDPOINT_POSITION = "/api/v1/queue/position";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        testRestTemplate.exchange(
            "/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(UserFixture.createRequest()),
            new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
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

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    class Enter {

        @DisplayName("인증된 유저가 대기열에 진입 시, 순번을 반환한다.")
        @Test
        void returnsPosition_whenAuthenticated() {
            // act
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ENTER, HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.EnterResponse>>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().position()).isEqualTo(1L)
            );
        }

        @DisplayName("이미 대기 중인 유저가 재진입 시, 기존 순번을 반환한다.")
        @Test
        void returnsExistingPosition_whenDuplicateEnter() {
            // arrange
            testRestTemplate.exchange(
                ENDPOINT_ENTER, HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.EnterResponse>>() {}
            );

            // act
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ENTER, HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.EnterResponse>>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().position()).isEqualTo(1L)
            );
        }

        @DisplayName("인증 헤더 없이 진입 시, 400을 반환한다.")
        @Test
        void returns400_whenNoAuthHeader() {
            // act
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ENTER, HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.EnterResponse>>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    class GetPosition {

        @DisplayName("대기 중인 유저가 순번 조회 시, 순번과 예상 대기 시간을 반환한다.")
        @Test
        void returnsPositionAndWaitTime_whenWaiting() {
            // arrange
            testRestTemplate.exchange(
                ENDPOINT_ENTER, HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.EnterResponse>>() {}
            );

            // act
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = testRestTemplate.exchange(
                ENDPOINT_POSITION, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().position()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().estimatedWaitSeconds()).isGreaterThanOrEqualTo(0L),
                () -> assertThat(response.getBody().data().token()).isNull()
            );
        }

        @DisplayName("대기열에 없는 유저가 순번 조회 시, 404를 반환한다.")
        @Test
        void returns404_whenNotInQueue() {
            // act
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = testRestTemplate.exchange(
                ENDPOINT_POSITION, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
