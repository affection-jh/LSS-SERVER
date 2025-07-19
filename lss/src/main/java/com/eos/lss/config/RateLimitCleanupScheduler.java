package com.eos.lss.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RateLimitCleanupScheduler {
    
    @Autowired
    private RateLimiter rateLimiter;
    
    /**
     * 매시간 Rate Limiter의 메모리를 정리
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다 (3600000ms)
    public void cleanupRateLimiter() {
        log.info("Rate Limiter 메모리 정리 시작");
        rateLimiter.cleanup();
        log.info("Rate Limiter 메모리 정리 완료");
    }
} 