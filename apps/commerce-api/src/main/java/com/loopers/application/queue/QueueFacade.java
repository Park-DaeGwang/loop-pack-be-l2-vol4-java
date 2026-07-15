package com.loopers.application.queue;

import com.loopers.domain.queue.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueFacade {

    private final WaitingQueueService waitingQueueService;

    public QueueInfo.EnterInfo enter(UUID userId) {
        long position = waitingQueueService.enter(userId);
        return QueueInfo.EnterInfo.from(position);
    }

    public QueueInfo.PositionInfo getPosition(UUID userId) {
        return QueueInfo.PositionInfo.from(waitingQueueService.getPosition(userId));
    }
}
