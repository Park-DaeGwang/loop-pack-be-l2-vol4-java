package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;

public class QueueV1Dto {

    public record EnterResponse(long position) {
        public static EnterResponse from(QueueInfo.EnterInfo info) {
            return new EnterResponse(info.position());
        }
    }

    public record PositionResponse(long position, long estimatedWaitSeconds, String token, long recommendedPollingIntervalSeconds) {
        public static PositionResponse from(QueueInfo.PositionInfo info) {
            return new PositionResponse(info.position(), info.estimatedWaitSeconds(), info.token(), info.recommendedPollingIntervalSeconds());
        }
    }
}
