package com.loopers.domain.metrics;

import java.util.Optional;
import java.util.UUID;

public interface ProductMetricsRepository {
    ProductMetricsModel save(ProductMetricsModel metrics);
    Optional<ProductMetricsModel> findByProductId(UUID productId);
}
