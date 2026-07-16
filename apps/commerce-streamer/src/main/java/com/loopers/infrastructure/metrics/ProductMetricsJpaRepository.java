package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, UUID> {
    Optional<ProductMetricsModel> findByProductIdAndDate(UUID productId, LocalDate date);
}
