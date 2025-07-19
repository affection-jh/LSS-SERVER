package com.eos.lss.config;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Map;

@Component
public class RateLimiter {
    
    // 사용자별 요청 기록을 저장하는 맵
    private final ConcurrentHashMap<String, UserRequestRecord> userRequests = new ConcurrentHashMap<>();
    
    // 기본 제한 설정
    private static final int DEFAULT_MAX_REQUESTS = 10; // 10초당 최대 10개 요청
    private static final int DEFAULT_WINDOW_SECONDS = 10; // 10초 윈도우
    
    // 특정 액션별 제한 설정
    private static final Map<String, RateLimitConfig> ACTION_LIMITS = Map.of(
        "coin-action", new RateLimitConfig(5, 5),      // 5초당 최대 5개 동전 액션
        "next-turn", new RateLimitConfig(3, 10),       // 10초당 최대 3개 턴 진행
        "register-order", new RateLimitConfig(2, 5),   // 5초당 최대 2개 순서 등록
        "start-playing", new RateLimitConfig(1, 10),   // 10초당 최대 1개 게임 시작
        "delete-session", new RateLimitConfig(1, 30)   // 30초당 최대 1개 세션 삭제
    );
    
    /**
     * 요청이 허용되는지 확인
     * @param userId 사용자 ID
     * @param action 액션 타입
     * @return 요청 허용 여부
     */
    public boolean isAllowed(String userId, String action) {
        if (userId == null || action == null) {
            return false;
        }
        
        UserRequestRecord record = userRequests.computeIfAbsent(userId, k -> new UserRequestRecord());
        RateLimitConfig config = ACTION_LIMITS.getOrDefault(action, new RateLimitConfig(DEFAULT_MAX_REQUESTS, DEFAULT_WINDOW_SECONDS));
        
        return record.isAllowed(action, config);
    }
    
    /**
     * 요청 기록
     * @param userId 사용자 ID
     * @param action 액션 타입
     */
    public void recordRequest(String userId, String action) {
        if (userId == null || action == null) {
            return;
        }
        
        UserRequestRecord record = userRequests.computeIfAbsent(userId, k -> new UserRequestRecord());
        record.recordRequest(action);
    }
    
    /**
     * 사용자별 요청 기록 클래스
     */
    private static class UserRequestRecord {
        private final ConcurrentHashMap<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, LocalDateTime> windowStart = new ConcurrentHashMap<>();
        
        public boolean isAllowed(String action, RateLimitConfig config) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime start = windowStart.get(action);
            
            // 윈도우가 만료되었거나 처음인 경우
            if (start == null || Duration.between(start, now).getSeconds() >= config.windowSeconds) {
                windowStart.put(action, now);
                requestCounts.put(action, new AtomicInteger(0));
                return true;
            }
            
            // 현재 윈도우 내에서 요청 수 확인
            AtomicInteger count = requestCounts.get(action);
            if (count == null) {
                count = new AtomicInteger(0);
                requestCounts.put(action, count);
            }
            
            return count.get() < config.maxRequests;
        }
        
        public void recordRequest(String action) {
            AtomicInteger count = requestCounts.get(action);
            if (count != null) {
                count.incrementAndGet();
            }
        }
    }
    
    /**
     * Rate Limit 설정 클래스
     */
    private static class RateLimitConfig {
        final int maxRequests;
        final int windowSeconds;
        
        RateLimitConfig(int maxRequests, int windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
        }
    }
    
    /**
     * 사용자 기록 정리 (메모리 누수 방지)
     */
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        userRequests.entrySet().removeIf(entry -> {
            UserRequestRecord record = entry.getValue();
            
            // 모든 액션의 윈도우가 만료되었는지 확인
            boolean allWindowsExpired = record.windowStart.entrySet().stream()
                .allMatch(windowEntry -> {
                    String action = windowEntry.getKey();
                    LocalDateTime start = windowEntry.getValue();
                    RateLimitConfig config = ACTION_LIMITS.getOrDefault(action, new RateLimitConfig(DEFAULT_MAX_REQUESTS, DEFAULT_WINDOW_SECONDS));
                    
                    // 윈도우 시간이 지났는지 확인
                    return Duration.between(start, now).getSeconds() >= config.windowSeconds;
                });
            
            // 모든 윈도우가 만료되었거나 요청 기록이 비어있으면 제거
            return record.requestCounts.isEmpty() || allWindowsExpired;
        });
    }
} 