package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public ProductMetricsModel save(ProductMetricsModel metrics) {
        return productMetricsJpaRepository.save(metrics);
    }

    @Override
    public Optional<ProductMetricsModel> findByProductIdAndDate(UUID productId, LocalDate date) {
        return productMetricsJpaRepository.findByProductIdAndDate(productId, date);
    }

    @Override
    public List<ProductMetricsModel> findAllByDate(LocalDate date) {
        return productMetricsJpaRepository.findAllByDate(date);
    }
}
