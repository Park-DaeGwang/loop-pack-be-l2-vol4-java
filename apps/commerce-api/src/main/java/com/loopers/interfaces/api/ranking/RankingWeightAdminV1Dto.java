package com.loopers.interfaces.api.ranking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class RankingWeightAdminV1Dto {

    public record UpdateRequest(
        @NotNull @Positive Double weight
    ) {}

    public record WeightResponse(String eventType, double weight) {}
}
