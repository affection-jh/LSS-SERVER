package com.eos.lss.dto;

import com.eos.lss.entity.SessionStatus;
import com.eos.lss.entity.CoinState;
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
    private SessionStatus status;
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
    private Boolean isLeeSoonSinByTimeExpired; // 이순신 상태가 시간 초과로 인한 것인지 구분
    
    // 게임 상태 상수
    public static final String STATE_WAITING_ROOM = "WAITING_ROOM";
    public static final String STATE_ORDER_REGISTER = "ORDER_REGISTER";
    public static final String STATE_GAME_PLAYING = "GAME_PLAYING";
    public static final String STATE_LEE_SOON_SIN = "LEE_SOON_SIN";
    public static final String STATE_GAME_FINISHED = "GAME_FINISHED";
} 