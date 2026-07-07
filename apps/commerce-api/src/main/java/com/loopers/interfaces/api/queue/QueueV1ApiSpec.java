package com.loopers.interfaces.api.queue;

import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue", description = "주문 대기열 API")
public interface QueueV1ApiSpec {

    @Operation(summary = "대기열 진입", description = "대기열에 진입하고 순번을 반환한다. 이미 진입한 경우 기존 순번을 반환한다.")
    ApiResponse<QueueV1Dto.EnterResponse> enter(UserModel user);

    @Operation(summary = "순번 조회", description = "현재 순번과 예상 대기 시간을 조회한다. 입장 토큰이 발급된 경우 토큰도 함께 반환한다.")
    ApiResponse<QueueV1Dto.PositionResponse> getPosition(UserModel user);
}
