package com.loopers.interfaces.api;

import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.ranking.RankingWeightAdminV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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
class RankingWeightAdminV1ApiE2ETest {

    private static final String WEIGHT_URL = "/api-admin/v1/rankings/weights";

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

    @Nested
    class 가중치_수정 {

        @Test
        void 정상_가중치_수정_시_200을_반환한다() {
            // arrange
            RankingWeightAdminV1Dto.UpdateRequest request = new RankingWeightAdminV1Dto.UpdateRequest(0.5);

            // act
            ResponseEntity<ApiResponse<RankingWeightAdminV1Dto.WeightResponse>> response =
                testRestTemplate.exchange(
                    WEIGHT_URL + "/VIEW", HttpMethod.PUT,
                    new HttpEntity<>(request, adminHeaders()),
                    new ParameterizedTypeReference<>() {}
                );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().eventType()).isEqualTo("VIEW"),
                () -> assertThat(response.getBody().data().weight()).isEqualTo(0.5)
            );
        }

        @Test
        void LDAP_헤더_없으면_401을_반환한다() {
            // arrange
            RankingWeightAdminV1Dto.UpdateRequest request = new RankingWeightAdminV1Dto.UpdateRequest(0.5);

            // act
            ResponseEntity<Void> response = testRestTemplate.exchange(
                WEIGHT_URL + "/VIEW", HttpMethod.PUT,
                new HttpEntity<>(request),
                Void.class
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void weight가_null이면_400을_반환한다() {
            // arrange — weight 필드 없는 빈 JSON
            String invalidBody = "{}";
            HttpHeaders headers = adminHeaders();
            headers.set("Content-Type", "application/json");

            // act
            ResponseEntity<Void> response = testRestTemplate.exchange(
                WEIGHT_URL + "/VIEW", HttpMethod.PUT,
                new HttpEntity<>(invalidBody, headers),
                Void.class
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void weight가_음수이면_400을_반환한다() {
            // arrange
            RankingWeightAdminV1Dto.UpdateRequest request = new RankingWeightAdminV1Dto.UpdateRequest(-0.1);

            // act
            ResponseEntity<Void> response = testRestTemplate.exchange(
                WEIGHT_URL + "/VIEW", HttpMethod.PUT,
                new HttpEntity<>(request, adminHeaders()),
                Void.class
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
