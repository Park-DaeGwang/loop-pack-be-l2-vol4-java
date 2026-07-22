package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "mv_product_rank_monthly",
    uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "year_month", "batch_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductRankMonthlyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID productId;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "year_month", nullable = false)
    private int yearMonth;

    @Column(name = "batch_id", nullable = false)
    private long batchId;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public MvProductRankMonthlyEntity(UUID productId, int rank, double score, int yearMonth, long batchId) {
        this.productId = productId;
        this.rank = rank;
        this.score = score;
        this.yearMonth = yearMonth;
        this.batchId = batchId;
        this.updatedAt = ZonedDateTime.now();
    }

    public void updateRank(int rank) {
        this.rank = rank;
        this.updatedAt = ZonedDateTime.now();
    }
}
