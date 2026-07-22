package com.loopers.domain.ranking;

public enum RankingType {
    DAILY("ranking:all:"),
    HOURLY("ranking:hourly:");

    private final String keyPrefix;

    RankingType(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String toKey(String date) {
        return keyPrefix + date;
    }
}
