package com.loopers.application.ranking;

import com.loopers.domain.product.ProductCacheDto;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingService rankingService;
    private final ProductService productService;

    public Page<RankingInfo> getTopRanked(String date, int page, int size) {
        List<UUID> productIds = rankingService.getTopRanked(date, page, size);
        long total = rankingService.countRanked(date);
        int startRank = (page - 1) * size + 1;

        List<RankingInfo> result = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            UUID productId = productIds.get(i);
            try {
                ProductCacheDto product = productService.getActiveSnapshot(productId);
                Double score = rankingService.getScore(date, productId);
                result.add(new RankingInfo(startRank + i, score, product.id(), product.name(), product.brandName(), product.price()));
            } catch (CoreException e) {
                if (e.getErrorType() == ErrorType.NOT_FOUND) {
                    continue;
                }
                throw e;
            }
        }
        return new PageImpl<>(result, PageRequest.of(page - 1, size), total);
    }

    public Long getRank(String date, UUID productId) {
        return rankingService.getRank(date, productId);
    }
}
