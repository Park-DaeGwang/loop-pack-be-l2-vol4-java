package com.loopers.application.queue;

import com.loopers.domain.queue.QueuePositionResult;

public class QueueInfo {

    public record EnterInfo(long position) {
        public static EnterInfo from(long position) {
            return new EnterInfo(position);
        }
    }

    public record PositionInfo(long position, long estimatedWaitSeconds, String token) {
        public static PositionInfo from(QueuePositionResult result) {
            return new PositionInfo(
                result.position(),
                result.estimatedWaitSeconds(),
                result.token().orElse(null)
            );
        }
    }
}
