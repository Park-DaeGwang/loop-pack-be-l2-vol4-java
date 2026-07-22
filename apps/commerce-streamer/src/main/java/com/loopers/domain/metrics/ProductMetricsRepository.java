package com.loopers.domain.metrics;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductMetricsRepository {
    ProductMetricsModel save(ProductMetricsModel metrics);
    Optional<ProductMetricsModel> findByProductIdAndDate(UUID productId, LocalDate date);
    List<ProductMetricsModel> findAllByDate(LocalDate date);
}
