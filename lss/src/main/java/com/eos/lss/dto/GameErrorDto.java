package com.eos.lss.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
// import lombok.AllArgsConstructor; // 중복 생성자 방지로 주석 처리

@Data
@NoArgsConstructor
// @AllArgsConstructor // 제거
public class GameErrorDto {
    private String errorType; // 에러 타입만 포함
    
    // 세션 관련 에러
    public static final String ERROR_SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    public static final String ERROR_SESSION_CREATION_FAILED = "SESSION_CREATION_FAILED";
    
    // 플레이어 관련 에러
    public static final String ERROR_PLAYER_NOT_FOUND = "PLAYER_NOT_FOUND";
    public static final String ERROR_PLAYER_ALREADY_JOINED = "PLAYER_ALREADY_JOINED";
    public static final String ERROR_PLAYER_DISCONNECTED = "PLAYER_DISCONNECTED";
    
    // 권한 관련 에러
    public static final String ERROR_NOT_PRESIDENT = "NOT_PRESIDENT";
    public static final String ERROR_NOT_CURRENT_TURN = "NOT_CURRENT_TURN";
    
    // 게임 상태 관련 에러
    public static final String ERROR_GAME_IN_PROGRESS = "GAME_IN_PROGRESS";
    public static final String ERROR_GAME_NOT_STARTED = "GAME_NOT_STARTED";
    public static final String ERROR_WRONG_GAME_STATE = "WRONG_GAME_STATE";
    public static final String ERROR_NOT_LEE_SOON_SIN_STATE = "NOT_LEE_SOON_SIN_STATE";
    
    // 게임 진행 관련 에러
    public static final String ERROR_INSUFFICIENT_PLAYERS = "INSUFFICIENT_PLAYERS";
    public static final String ERROR_PRESIDENT_LEFT = "PRESIDENT_LEFT";
    public static final String ERROR_ORDER_NOT_REGISTERED = "ORDER_NOT_REGISTERED";
    public static final String ERROR_ALREADY_ORDERED = "ALREADY_ORDERED";
    public static final String ERROR_COIN_NOT_SET = "COIN_NOT_SET";
    public static final String ERROR_GAME_TIME_EXPIRED = "GAME_TIME_EXPIRED";
    public static final String ERROR_LEE_SOON_SIN_TRIGGERED = "LEE_SOON_SIN_TRIGGERED";
    
    // 턴 스킵 관련 에러
    public static final String ERROR_TURN_SKIPPED = "TURN_SKIPPED";
    public static final String ERROR_PLAYER_TIMEOUT = "PLAYER_TIMEOUT";
    
    // 동전 관련 에러
    public static final String ERROR_INVALID_COIN_TYPE = "INVALID_COIN_TYPE";
    public static final String ERROR_INVALID_COIN_STATE = "INVALID_COIN_STATE";
    
    // 입장 코드 관련 에러
    public static final String ERROR_INVALID_ENTRY_CODE = "INVALID_ENTRY_CODE";
    
    // 네트워크 관련 에러
    public static final String ERROR_NETWORK_TIMEOUT = "NETWORK_TIMEOUT";
    public static final String ERROR_CONNECTION_LOST = "CONNECTION_LOST";
    
    // 시스템 에러
    public static final String ERROR_INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    
    // 편의 생성자
    public GameErrorDto(String errorType) {
        this.errorType = errorType;
    }
} 