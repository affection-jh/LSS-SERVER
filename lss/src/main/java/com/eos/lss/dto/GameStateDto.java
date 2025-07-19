package com.eos.lss.dto;

import com.eos.lss.entity.CoinState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameStateDto {
    private String sessionId;
    private String entryCode;
    private String presidentId;
    private LocalDateTime createdAt;
    private List<PlayerDto> players; // 게임 상태에 따라 적절한 플레이어 리스트
    private int currentPlayerIndex;
    private boolean isClockWise;
    private CoinState firstCoinState;
    private CoinState secondCoinState;
    
    // 게임 상태 관련 추가 정보
    private PlayerDto currentPlayer; // 현재 턴인 플레이어
    private boolean isMyTurn; // 요청한 사용자의 턴인지 여부
    private boolean isPresident; // 요청한 사용자가 방장인지 여부
    private String gameState; // 현재 게임 상태 (String)
    
    // 게임 마감 시간 관련
    private LocalDateTime gameEndTime; // 게임 마감 시간
    private Boolean leeSoonSinByTimeExpired; // 이순신 상태가 시간 초과로 인한 것인지 구분
    
    // 게임 상태 상수
    public static final String STATE_WAITING_ROOM = "WAITING_ROOM";
    public static final String STATE_ORDER_REGISTER = "ORDER_REGISTER";
    public static final String STATE_GAME_PLAYING = "GAME_PLAYING";
    public static final String STATE_LEE_SOON_SIN = "LEE_SOON_SIN";

    
    // 내부 상태 상수 (데이터베이스 저장용)
    public static final String STATE_ORDERING = "ORDERING";
    public static final String STATE_ON_GOING = "ON_GOING";
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    static {
        // LocalDateTime 직렬화를 위한 모듈 등록
        objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public String toString() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (Exception e) {
            return "{\"error\":\"JSON 직렬화 실패: " + e.getMessage() + "\"}";
        }
    }
} 