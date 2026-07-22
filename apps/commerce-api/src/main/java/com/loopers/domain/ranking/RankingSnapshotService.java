package com.loopers.domain.ranking;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingSnapshotService {

    private static final int SNAPSHOT_SIZE = 200;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZONE);

    private final RankingRepository rankingRepository;

    private final Map<String, List<UUID>> snapshot = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedDelay = 60_000)
    public void refresh() {
        String todayKey = RankingType.DAILY.toKey(LocalDate.now(ZONE).format(DATE_FORMAT));
        try {
            List<UUID> ranking = rankingRepository.findTopRanked(todayKey, 0, SNAPSHOT_SIZE);
            snapshot.put(todayKey, ranking);
        } catch (Exception e) {
            log.warn("랭킹 스냅샷 갱신 실패 — 기존 스냅샷 유지, key={}", todayKey);
        }
    }

    public List<UUID> get(String key, int offset, int limit) {
        List<UUID> cached = snapshot.getOrDefault(key, List.of());
        if (offset >= cached.size()) {
            return List.of();
        }
        return cached.subList(offset, Math.min(offset + limit, cached.size()));
    }

    public long count(String key) {
        return snapshot.getOrDefault(key, List.of()).size();
    }
}
