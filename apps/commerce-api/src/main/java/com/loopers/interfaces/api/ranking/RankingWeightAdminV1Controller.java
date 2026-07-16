package com.loopers.interfaces.api.ranking;

import com.loopers.domain.ranking.RankingWeightService;
import com.loopers.interfaces.api.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/rankings/weights")
public class RankingWeightAdminV1Controller implements RankingWeightAdminV1ApiSpec {

    private final RankingWeightService rankingWeightService;

    @Override
    @PutMapping("/{eventType}")
    public ApiResponse<RankingWeightAdminV1Dto.WeightResponse> updateWeight(
        @PathVariable String eventType,
        @RequestBody @Valid RankingWeightAdminV1Dto.UpdateRequest request
    ) {
        rankingWeightService.updateWeight(eventType, request.weight());
        return ApiResponse.success(new RankingWeightAdminV1Dto.WeightResponse(eventType, request.weight()));
    }
}
