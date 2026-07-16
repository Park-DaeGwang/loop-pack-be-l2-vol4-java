package com.loopers.interfaces.api.ranking;

import com.loopers.domain.ranking.RankingType;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

public interface RankingV1ApiSpec {

    @GetMapping
    ApiResponse<PageResponse<RankingV1Dto.RankingResponse>> getTopRanked(
        @RequestParam(defaultValue = "DAILY") RankingType type,
        @RequestParam String date,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "1") int page
    );
}
