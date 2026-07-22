package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api-admin/v1/rankings/weights")
public interface RankingWeightAdminV1ApiSpec {

    @PutMapping("/{eventType}")
    ApiResponse<RankingWeightAdminV1Dto.WeightResponse> updateWeight(
        @PathVariable String eventType,
        @RequestBody @Valid RankingWeightAdminV1Dto.UpdateRequest request
    );
}
