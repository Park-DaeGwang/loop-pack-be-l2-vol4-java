package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

public interface RankingV1ApiSpec {

    @GetMapping
    ApiResponse<PageResponse<RankingV1Dto.RankingResponse>> getTopRanked(
        @RequestParam String date,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "1") int page
    );
}
