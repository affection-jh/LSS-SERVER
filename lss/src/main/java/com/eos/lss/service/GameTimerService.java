package com.eos.lss.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameTimerService {


    private final SessionService sessionService;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);
    
    // 세션별 타이머 Future를 저장하여 취소 가능하게 함
    private final Map<String, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    /**
     * 게임 마감 시간을 정확히 스케줄링
     * @param sessionId 세션 ID
     * @param endTime 마감 시간
     */
    public void scheduleGameEnd(String sessionId, LocalDateTime endTime) {
        // 기존 타이머가 있다면 취소
        cancelGameTimer(sessionId);
        
        long delay = Duration.between(LocalDateTime.now(), endTime).toMillis();
        
        if (delay <= 0) {
            // 이미 시간이 지난 경우 즉시 실행

            sessionService.checkGameEndTime(sessionId);
            return;
        }
        

        
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {

                sessionService.checkGameEndTime(sessionId);
                // 타이머 실행 완료 후 Map에서 제거
                activeTimers.remove(sessionId);
            } catch (Exception e) {

                activeTimers.remove(sessionId);
            }
        }, delay, TimeUnit.MILLISECONDS);
        
        // 새로운 타이머를 Map에 저장
        activeTimers.put(sessionId, future);
    }

    /**
     * 타이머 취소 (게임이 조기 종료된 경우)
     * @param sessionId 세션 ID
     */
    public void cancelGameTimer(String sessionId) {
        ScheduledFuture<?> future = activeTimers.get(sessionId);
        if (future != null && !future.isDone()) {
            future.cancel(false);

        }
        activeTimers.remove(sessionId);
    }

    /**
     * 서버 종료 시 모든 타이머 정리
     */
    @PreDestroy
    public void shutdown() {

        
        // 모든 활성 타이머 취소
        for (Map.Entry<String, ScheduledFuture<?>> entry : activeTimers.entrySet()) {
        
            ScheduledFuture<?> future = entry.getValue();
            
            if (!future.isDone()) {
                future.cancel(false);

            }
        }
        
        activeTimers.clear();
        
        // ExecutorService 종료
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        

    }
} 