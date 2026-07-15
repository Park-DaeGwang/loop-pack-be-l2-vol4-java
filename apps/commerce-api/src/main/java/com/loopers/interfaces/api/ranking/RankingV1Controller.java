package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private final RankingFacade rankingFacade;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<RankingV1Dto.RankingResponse>> getTopRanked(
        @RequestParam String date,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "1") int page
    ) {
        Page<RankingV1Dto.RankingResponse> result = rankingFacade.getTopRanked(date, page, size)
            .map(RankingV1Dto.RankingResponse::from);
        return ApiResponse.success(PageResponse.from(result));
    }
}
