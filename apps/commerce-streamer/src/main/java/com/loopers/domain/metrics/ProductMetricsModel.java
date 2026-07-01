package com.loopers.domain.metrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsModel extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true, updatable = false)
    private UUID productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount = 0;

    @Column(name = "view_count", nullable = false)
    private long viewCount = 0;

    @Column(name = "sales_count", nullable = false)
    private long salesCount = 0;

    @Column(name = "last_event_at")
    private ZonedDateTime lastEventAt;

    public ProductMetricsModel(UUID productId) {
        this.productId = productId;
    }

    /** Kafka 파티션 순서 보장은 되지만, 리밸런스/재처리 등으로 뒤늦게 온 오래된 이벤트를 걸러내는 안전장치 */
    public boolean isStale(ZonedDateTime eventTime) {
        return lastEventAt != null && !eventTime.isAfter(lastEventAt);
    }

    public void incrementLike(ZonedDateTime eventTime) {
        this.likeCount++;
        this.lastEventAt = eventTime;
    }

    public void decrementLike(ZonedDateTime eventTime) {
        this.likeCount = Math.max(0, this.likeCount - 1);
        this.lastEventAt = eventTime;
    }

    public void incrementView(ZonedDateTime eventTime) {
        this.viewCount++;
        this.lastEventAt = eventTime;
    }

    public void incrementSales(ZonedDateTime eventTime) {
        this.salesCount++;
        this.lastEventAt = eventTime;
    }
}
