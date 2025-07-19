package com.eos.lss.websocket;

import com.eos.lss.service.SessionService;
import com.eos.lss.config.RateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

@Component
@Slf4j
public class SimpleWebSocketHandler extends TextWebSocketHandler {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    // WebSocket 세션 ID와 사용자 ID 매핑
    private final ConcurrentHashMap<String, String> sessionToUserMap = new ConcurrentHashMap<>();
    // 사용자 ID와 WebSocket 세션 ID 매핑
    private final ConcurrentHashMap<String, String> userToSessionMap = new ConcurrentHashMap<>();
    // WebSocket 세션 ID와 게임 세션 ID 매핑 (새로 추가)
    private final ConcurrentHashMap<String, String> sessionToGameSessionMap = new ConcurrentHashMap<>();
    // 게임 세션 ID와 WebSocket 세션 ID들 매핑 (새로 추가)
    private final ConcurrentHashMap<String, Set<String>> gameSessionToWebSocketSessionsMap = new ConcurrentHashMap<>();
    
    @Autowired
    @Lazy
    private SessionService sessionService;
    
    @Autowired
    private RateLimiter rateLimiter;

    // 메시지 타입 - 단순화
    public static final String MSG_TYPE_OK = "ok";
    public static final String MSG_TYPE_ERROR = "error";

    public SimpleWebSocketHandler() {
        // LocalDateTime 직렬화를 위한 모듈 등록
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("=== WebSocket 연결 성공 ===");
        log.info("세션 ID: {}", session.getId());
        log.info("원격 주소: {}", session.getRemoteAddress());
        
        sessions.put(session.getId(), session);
        
        // 연결 성공 메시지 전송 (기본 GameStateDto 포함)
        String response = "{\"type\":\"" + MSG_TYPE_OK + "\",\"status\":\"connected\",\"sessionId\":\"" + session.getId() + "\",\"entryCode\":null,\"presidentId\":null,\"createdAt\":null,\"players\":[],\"currentPlayerIndex\":0,\"isClockWise\":true,\"firstCoinState\":null,\"secondCoinState\":null,\"currentPlayer\":null,\"isMyTurn\":false,\"isPresident\":false,\"gameState\":\"DISCONNECTED\",\"gameEndTime\":null,\"isLeeSoonSinByTimeExpired\":null}";
        session.sendMessage(new TextMessage(response));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("=== WebSocket 메시지 수신 ===");
        log.info("세션 ID: {}", session.getId());
        log.info("메시지: {}", message.getPayload());
        
        try {
            String payload = message.getPayload();
            JsonNode jsonNode = objectMapper.readTree(payload);
            
            // 일반 JSON 메시지 처리
            log.info("JSON 메시지 처리");
            handleJsonMessage(session, jsonNode);
            
        } catch (Exception e) {
            log.error("메시지 처리 오류: {}", e.getMessage(), e);
            
            // 세션이 열려있는 경우에만 에러 응답 전송
            if (session.isOpen()) {
                try {
                    String errorResponse = createErrorResponse("INTERNAL_SERVER_ERROR", e.getMessage());
                    session.sendMessage(new TextMessage(errorResponse));
                } catch (IOException ioException) {
                    log.error("에러 응답 전송 실패: {}", ioException.getMessage());
                    // 세션에 문제가 있으면 제거
                    handleUserDisconnection(session.getId());
                    sessions.remove(session.getId());
                }
            } else {
                log.warn("닫힌 세션에서 메시지 처리 오류 발생: {}", session.getId());
                // 닫힌 세션 제거
                handleUserDisconnection(session.getId());
                sessions.remove(session.getId());
            }
        }
    }
    
    private void handleCreateSession(WebSocketSession session, JsonNode payload) throws Exception {
        String userId = payload.get("userId").asText();
        String name = payload.get("name").asText();
        
        log.info("세션 생성 요청 - userId: {}, name: {}", userId, name);
        
        try {
            // 사용자 매핑 등록
            registerUserMapping(session.getId(), userId);
            
            String sessionId = sessionService.createSession(userId, name);
            
            // 게임 세션 매핑 등록 (새로 추가)
            registerGameSessionMapping(session.getId(), sessionId);
            
            // 게임 상태 가져오기
            var gameState = sessionService.getGameState(sessionId, userId);
            
            // 통일된 응답 구조
            String response = createGameStateResponse(gameState);
            session.sendMessage(new TextMessage(response));
        } catch (Exception e) {
            log.error("세션 생성 중 오류 발생: {}", e.getMessage(), e);
            String errorResponse = createErrorResponse("SESSION_CREATION_FAILED", "세션 생성 중 오류가 발생했습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        }
    }
    
    private void handleJoinSession(WebSocketSession session, JsonNode payload) throws Exception {
        String entryCode = payload.get("entryCode").asText();
        String userId = payload.get("userId").asText();
        String name = payload.get("name").asText();
        
        log.info("세션 입장 요청 - entryCode: {}, userId: {}, name: {}", entryCode, userId, name);
        
        try {
            // 사용자 매핑 등록
            registerUserMapping(session.getId(), userId);
            
            String sessionId = sessionService.joinSession(entryCode, userId, name);
            
            // 게임 세션 매핑 등록 (새로 추가)
            registerGameSessionMapping(session.getId(), sessionId);
            
            // 게임 상태 가져오기
            var gameState = sessionService.getGameState(sessionId, userId);
            
            // 통일된 응답 구조
            String response = createGameStateResponse(gameState);
            session.sendMessage(new TextMessage(response));
        } catch (com.eos.lss.exception.SessionNotFoundException e) {
            log.warn("세션을 찾을 수 없음 - entryCode: {}, error: {}", entryCode, e.getMessage());
            String errorResponse = createErrorResponse("INVALID_ENTRY_CODE", "입장 코드가 올바르지 않습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (com.eos.lss.exception.InvalidGameStateException e) {
            log.warn("게임 상태 오류 - entryCode: {}, error: {}", entryCode, e.getMessage());
            String errorResponse = createErrorResponse("GAME_IN_PROGRESS", "이미 진행중인 게임입니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (com.eos.lss.exception.PlayerAlreadyJoinedException e) {
            log.warn("이미 참여한 플레이어 - entryCode: {}, userId: {}, error: {}", entryCode, userId, e.getMessage());
            String errorResponse = createErrorResponse("PLAYER_ALREADY_JOINED", "이미 참여한 플레이어입니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (Exception e) {
            log.error("세션 입장 중 오류 발생: {}", e.getMessage(), e);
            String errorResponse = createErrorResponse("INTERNAL_SERVER_ERROR", "세션 입장 중 오류가 발생했습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        }
    }
    
    private void handleCoinAction(WebSocketSession session, JsonNode payload) throws Exception {
        String sessionId = payload.get("sessionId").asText();
        String coinType = payload.get("coinType").asText();
        String state = payload.get("state").asText();
        String userId = payload.get("userId").asText();
        
 
        
        try {
            sessionService.setCoinState(sessionId, coinType, state);
            
            // 게임 상태 가져오기
            var gameState = sessionService.getGameState(sessionId, userId);
            
            // 통일된 응답 구조
            String response = createGameStateResponse(gameState);
            session.sendMessage(new TextMessage(response));
        } catch (com.eos.lss.exception.SessionNotFoundException e) {
            log.warn("세션을 찾을 수 없음 - sessionId: {}, error: {}", sessionId, e.getMessage());
            String errorResponse = createErrorResponse("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (com.eos.lss.exception.InvalidGameStateException e) {
            log.warn("게임 상태 오류 - sessionId: {}, userId: {}, error: {}", sessionId, userId, e.getMessage());
            String errorResponse = createErrorResponse("NOT_REGISTERED_PLAYER", e.getMessage());
            session.sendMessage(new TextMessage(errorResponse));
        } catch (IllegalArgumentException e) {
            log.warn("잘못된 동전 상태 - coinType: {}, state: {}, error: {}", coinType, state, e.getMessage());
            String errorResponse = createErrorResponse("INVALID_COIN_STATE", "잘못된 동전 상태입니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (Exception e) {
            log.error("동전 액션 중 오류 발생: {}", e.getMessage(), e);
            String errorResponse = createErrorResponse("INTERNAL_SERVER_ERROR", "동전 액션 중 오류가 발생했습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        }
    }
    
    private void handleNextTurn(WebSocketSession session, JsonNode payload) throws Exception {
        String sessionId = payload.get("sessionId").asText();
        String userId = payload.get("userId").asText();
        
        try {
            sessionService.nextTurn(sessionId);
            
            // 게임 상태 가져오기
            var gameState = sessionService.getGameState(sessionId, userId);
            
            // 통일된 응답 구조
            String response = createGameStateResponse(gameState);
            session.sendMessage(new TextMessage(response));
        } catch (com.eos.lss.exception.SessionNotFoundException e) {
            log.warn("세션을 찾을 수 없음 - sessionId: {}, error: {}", sessionId, e.getMessage());
            String errorResponse = createErrorResponse("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (com.eos.lss.exception.InvalidGameStateException e) {
            log.warn("게임 상태 오류 - sessionId: {}, userId: {}, error: {}", sessionId, userId, e.getMessage());
            String errorResponse = createErrorResponse("NOT_REGISTERED_PLAYER", e.getMessage());
            session.sendMessage(new TextMessage(errorResponse));
        } catch (Exception e) {
            log.error("다음 턴 처리 중 오류 발생: {}", e.getMessage(), e);
            String errorResponse = createErrorResponse("INTERNAL_SERVER_ERROR", "다음 턴 처리 중 오류가 발생했습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        }
    }
    
    private void handleRegisterOrder(WebSocketSession session, JsonNode payload) throws Exception {
        String sessionId = payload.get("sessionId").asText();
        String userId = payload.get("userId").asText();
        
        try {
            sessionService.registerOrder(sessionId, userId);
            
            // 게임 상태 가져오기
            var gameState = sessionService.getGameState(sessionId, userId);
            
            // 통일된 응답 구조
            String response = createGameStateResponse(gameState);
            session.sendMessage(new TextMessage(response));
        } catch (com.eos.lss.exception.SessionNotFoundException e) {
            log.warn("세션을 찾을 수 없음 - sessionId: {}, error: {}", sessionId, e.getMessage());
            String errorResponse = createErrorResponse("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (com.eos.lss.exception.InvalidGameStateException e) {
            log.warn("게임 상태 오류 - sessionId: {}, userId: {}, error: {}", sessionId, userId, e.getMessage());
            String errorResponse = createErrorResponse("WRONG_GAME_STATE", e.getMessage());
            session.sendMessage(new TextMessage(errorResponse));
        } catch (IllegalArgumentException e) {
            log.warn("플레이어를 찾을 수 없음 - sessionId: {}, userId: {}, error: {}", sessionId, userId, e.getMessage());
            String errorResponse = createErrorResponse("PLAYER_NOT_FOUND", "플레이어를 찾을 수 없습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (Exception e) {
            log.error("순서 등록 중 오류 발생: {}", e.getMessage(), e);
            String errorResponse = createErrorResponse("INTERNAL_SERVER_ERROR", "순서 등록 중 오류가 발생했습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        }
    }
    
    private void handleStartOrdering(WebSocketSession session, JsonNode payload) throws Exception {
        String sessionId = payload.get("sessionId").asText();
        String userId = payload.get("userId").asText();
        
        try {
            sessionService.startGame(sessionId);
            
            // 게임 상태 가져오기
            var gameState = sessionService.getGameState(sessionId, userId);
            
            // 통일된 응답 구조
            String response = createGameStateResponse(gameState);
            session.sendMessage(new TextMessage(response));
        } catch (com.eos.lss.exception.SessionNotFoundException e) {
            log.warn("세션을 찾을 수 없음 - sessionId: {}, error: {}", sessionId, e.getMessage());
            String errorResponse = createErrorResponse("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (Exception e) {
            log.error("순서 등록 시작 중 오류 발생: {}", e.getMessage(), e);
            String errorResponse = createErrorResponse("INTERNAL_SERVER_ERROR", "순서 등록 시작 중 오류가 발생했습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        }
    }
    
    private void handleStartPlaying(WebSocketSession session, JsonNode payload) throws Exception {
        String sessionId = payload.get("sessionId").asText();
        String userId = payload.get("userId").asText();
        
        try {
            sessionService.startPlaying(sessionId);
            
            // 게임 상태 가져오기
            var gameState = sessionService.getGameState(sessionId, userId);
            
            // 통일된 응답 구조
            String response = createGameStateResponse(gameState);
            session.sendMessage(new TextMessage(response));
        } catch (com.eos.lss.exception.SessionNotFoundException e) {
            log.warn("세션을 찾을 수 없음 - sessionId: {}, error: {}", sessionId, e.getMessage());
            String errorResponse = createErrorResponse("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (com.eos.lss.exception.InvalidGameStateException e) {
            log.warn("게임 상태 오류 - sessionId: {}, error: {}", sessionId, e.getMessage());
            String errorResponse = createErrorResponse("INSUFFICIENT_PLAYERS", e.getMessage());
            session.sendMessage(new TextMessage(errorResponse));
        } catch (Exception e) {
            log.error("start-playing 처리 중 오류 발생: {}", e.getMessage(), e);
            String errorResponse = createErrorResponse("INTERNAL_SERVER_ERROR", "게임 시작 중 오류가 발생했습니다: " + e.getMessage());
            session.sendMessage(new TextMessage(errorResponse));
        }
    }
    
    private void handleContinueLeeSoonSin(WebSocketSession session, JsonNode payload) throws Exception {
        String sessionId = payload.get("sessionId").asText();
        String userId = payload.get("userId").asText();
        
        log.info("=== 이순신 계속하기 액션 처리 시작 ===");
        log.info("세션 ID: {}", sessionId);
        log.info("사용자 ID: {}", userId);
        
        try {
            sessionService.continueFromLeeSoonSin(sessionId);
            
            // 게임 상태 가져오기
            var gameState = sessionService.getGameState(sessionId, userId);
            
            // 통일된 응답 구조
            String response = createGameStateResponse(gameState);
            session.sendMessage(new TextMessage(response));
        } catch (com.eos.lss.exception.SessionNotFoundException e) {
            log.warn("세션을 찾을 수 없음 - sessionId: {}, error: {}", sessionId, e.getMessage());
            String errorResponse = createErrorResponse("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (com.eos.lss.exception.InvalidGameStateException e) {
            log.warn("게임 상태 오류 - sessionId: {}, userId: {}, error: {}", sessionId, userId, e.getMessage());
            String errorResponse = createErrorResponse("NOT_REGISTERED_PLAYER", e.getMessage());
            session.sendMessage(new TextMessage(errorResponse));
        } catch (Exception e) {
            log.error("이순신 계속하기 중 오류 발생: {}", e.getMessage(), e);
            String errorResponse = createErrorResponse("INTERNAL_SERVER_ERROR", "이순신 계속하기 중 오류가 발생했습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        }
    }
    
    private void handleGetState(WebSocketSession session, JsonNode payload) throws Exception {
        String sessionId = payload.get("sessionId").asText();
        String userId = payload.get("userId").asText();
        
        try {
            var gameState = sessionService.getGameState(sessionId, userId);
            
            // 통일된 응답 구조
            String response = createGameStateResponse(gameState);
        session.sendMessage(new TextMessage(response));
        } catch (com.eos.lss.exception.SessionNotFoundException e) {
            log.warn("세션을 찾을 수 없음 - sessionId: {}, error: {}", sessionId, e.getMessage());
            String errorResponse = createErrorResponse("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (com.eos.lss.exception.InvalidGameStateException e) {
            log.warn("게임 상태 오류 - sessionId: {}, userId: {}, error: {}", sessionId, userId, e.getMessage());
            String errorResponse = createErrorResponse("NOT_REGISTERED_PLAYER", e.getMessage());
            session.sendMessage(new TextMessage(errorResponse));
        } catch (Exception e) {
            log.error("게임 상태 조회 중 오류 발생: {}", e.getMessage(), e);
            String errorResponse = createErrorResponse("INTERNAL_SERVER_ERROR", "게임 상태 조회 중 오류가 발생했습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        }
    }
    
    private void handleDeleteSession(WebSocketSession session, JsonNode payload) throws Exception {
        String sessionId = payload.get("sessionId").asText();
        String userId = payload.get("userId").asText();
        
        try {
            sessionService.deleteSession(sessionId, userId);
            
            // 게임 상태 가져오기 (삭제된 세션의 경우 기본 상태)
            var gameState = sessionService.getGameState(sessionId, userId);
            
            // 통일된 응답 구조
            String response = createGameStateResponse(gameState);
            session.sendMessage(new TextMessage(response));
        } catch (com.eos.lss.exception.SessionNotFoundException e) {
            log.warn("세션을 찾을 수 없음 - sessionId: {}, error: {}", sessionId, e.getMessage());
            String errorResponse = createErrorResponse("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (IllegalArgumentException e) {
            log.warn("권한 없음 - sessionId: {}, userId: {}, error: {}", sessionId, userId, e.getMessage());
            String errorResponse = createErrorResponse("NOT_PRESIDENT", "방장만 세션을 삭제할 수 있습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        } catch (Exception e) {
            log.error("세션 삭제 중 오류 발생: {}", e.getMessage(), e);
            String errorResponse = createErrorResponse("INTERNAL_SERVER_ERROR", "세션 삭제 중 오류가 발생했습니다.");
            session.sendMessage(new TextMessage(errorResponse));
        }
    }

    private void handleJsonMessage(WebSocketSession session, JsonNode jsonNode) throws Exception {
        // type 또는 action 필드에서 메시지 타입 추출
        String type = "";
        if (jsonNode.has("type")) {
            type = jsonNode.get("type").asText();
        } else if (jsonNode.has("action")) {
            type = jsonNode.get("action").asText();
        }
        
        log.info("메시지 타입: '{}', 전체 메시지: {}", type, jsonNode.toString());
        log.info("jsonNode.has('action'): {}, jsonNode.has('data'): {}", jsonNode.has("action"), jsonNode.has("data"));
        
        // action 필드가 있는 경우 payload에서 데이터 추출
        JsonNode payload = jsonNode;
        if (jsonNode.has("action") && jsonNode.has("data")) {
            payload = jsonNode.get("data");
            log.info("data 필드 추출됨: {}", payload.toString());
        } else if (jsonNode.has("action")) {
            // action만 있고 data가 없는 경우 (action 필드의 값들이 직접 payload)
            payload = jsonNode;
            log.info("action만 있음, 전체를 payload로 사용: {}", payload.toString());
        }
        
        log.info("최종 처리할 payload: {}", payload.toString());
        
        // Rate Limiting 체크
        String userId = null;
        if (payload.has("userId")) {
            userId = payload.get("userId").asText();
        }
        
        if (userId != null && !rateLimiter.isAllowed(userId, type)) {
            log.warn("Rate limit exceeded - userId: {}, action: {}", userId, type);
            String errorResponse = createErrorResponse("RATE_LIMIT_EXCEEDED", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
            session.sendMessage(new TextMessage(errorResponse));
            return;
        }
        
        // Rate Limiting 기록
        if (userId != null) {
            rateLimiter.recordRequest(userId, type);
        }
        
        switch (type) {
            case "create-session":
                handleCreateSession(session, payload);
                break;
            case "join-session":
                handleJoinSession(session, payload);
                break;
            case "coin-action":
                handleCoinAction(session, payload);
                break;
            case "next-turn":
                handleNextTurn(session, payload);
                break;
            case "register-order":
                handleRegisterOrder(session, payload);
                break;
            case "start-ordering":
                handleStartOrdering(session, payload);
                break;
            case "start-playing":
                handleStartPlaying(session, payload);
                break;
            case "continue-lee-soon-sin":
                handleContinueLeeSoonSin(session, payload);
                break;
            case "get-state":
                handleGetState(session, payload);
                break;
            case "delete-session":
                handleDeleteSession(session, payload);
                break;
            default:
                log.warn("알 수 없는 메시지 타입: '{}', 전체 메시지: {}", type, jsonNode.toString());
                String errorResponse = createErrorResponse("INVALID_MESSAGE_TYPE", "알 수 없는 메시지 타입: " + type);
                session.sendMessage(new TextMessage(errorResponse));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("=== WebSocket 연결 종료 ===");
        log.info("세션 ID: {}", session.getId());
        log.info("종료 상태: {}", status);
        
        // 사용자 연결 끊김 처리
        handleUserDisconnection(session.getId());
        
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("=== WebSocket 전송 오류 ===");
        log.error("세션 ID: {}", session.getId());
        log.error("오류: {}", exception.getMessage(), exception);
        
        // 사용자 연결 끊김 처리
        handleUserDisconnection(session.getId());
        
        sessions.remove(session.getId());
    }

    // 특정 게임 세션에만 브로드캐스트 (새로 추가)
    public void broadcastToGameSession(String gameSessionId, String message) {
        Set<String> webSocketSessionIds = gameSessionToWebSocketSessionsMap.get(gameSessionId);
        if (webSocketSessionIds == null) {
            log.warn("게임 세션 {}에 연결된 WebSocket 세션이 없습니다.", gameSessionId);
            return;
        }
        
        List<String> closedSessions = new ArrayList<>();
        
        webSocketSessionIds.forEach(webSocketSessionId -> {
            WebSocketSession session = sessions.get(webSocketSessionId);
            if (session != null) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(message));
                    } else {
                        // 닫힌 세션은 나중에 제거하기 위해 목록에 추가
                        closedSessions.add(webSocketSessionId);
                    }
                } catch (IOException e) {
                    log.error("게임 세션 브로드캐스트 오류: {}", e.getMessage());
                    // 오류 발생한 세션도 제거 목록에 추가
                    closedSessions.add(webSocketSessionId);
                }
            } else {
                // 세션이 없으면 제거 목록에 추가
                closedSessions.add(webSocketSessionId);
            }
        });
        
        // 닫힌 세션들 제거
        closedSessions.forEach(sessionId -> {
            log.info("닫힌 세션 제거: {}", sessionId);
            handleUserDisconnection(sessionId);
            sessions.remove(sessionId);
            webSocketSessionIds.remove(sessionId);
        });
    }

    // 모든 세션에 브로드캐스트 (기존 메서드 유지, 하지만 사용하지 않음)
    public void broadcastToAll(String message) {
        List<String> closedSessions = new ArrayList<>();
        
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
                } else {
                    // 닫힌 세션은 나중에 제거하기 위해 목록에 추가
                    closedSessions.add(session.getId());
                }
            } catch (IOException e) {
                log.error("브로드캐스트 오류: {}", e.getMessage());
                // 오류 발생한 세션도 제거 목록에 추가
                closedSessions.add(session.getId());
            }
        });
        
        // 닫힌 세션들 제거
        closedSessions.forEach(sessionId -> {
            log.info("닫힌 세션 제거: {}", sessionId);
            handleUserDisconnection(sessionId);
            sessions.remove(sessionId);
        });
    }

    // 특정 세션에 메시지 전송
    public void sendToSession(String sessionId, String message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null) {
            try {
                if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
                } else {
                    log.warn("닫힌 세션에 메시지 전송 시도: {}", sessionId);
                    // 닫힌 세션 제거
                    handleUserDisconnection(sessionId);
                    sessions.remove(sessionId);
                }
            } catch (IOException e) {
                log.error("세션 메시지 전송 오류: {}", e.getMessage());
                // 오류 발생한 세션 제거
                handleUserDisconnection(sessionId);
                sessions.remove(sessionId);
            }
        }
    }
    
    // 특정 게임 세션의 특정 사용자에게 메시지 전송
    public void sendToUserInGameSession(String gameSessionId, String userId, String message) {
        String webSocketSessionId = userToSessionMap.get(userId);
        if (webSocketSessionId == null) {
            log.warn("사용자 {}의 WebSocket 세션을 찾을 수 없습니다.", userId);
            return;
        }
        
        WebSocketSession session = sessions.get(webSocketSessionId);
        if (session != null) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                } else {
                    log.warn("닫힌 세션에 메시지 전송 시도: {}", webSocketSessionId);
                    // 닫힌 세션 제거
                    handleUserDisconnection(webSocketSessionId);
                    sessions.remove(webSocketSessionId);
                }
            } catch (IOException e) {
                log.error("사용자 메시지 전송 오류: {}", e.getMessage());
                // 오류 발생한 세션 제거
                handleUserDisconnection(webSocketSessionId);
                sessions.remove(webSocketSessionId);
            }
        }
    }
    
    // 통일된 게임 상태 응답 생성
    private String createGameStateResponse(com.eos.lss.dto.GameStateDto gameState) {
        try {
            return "{\"type\":\"" + MSG_TYPE_OK + "\",\"sessionId\":\"" + gameState.getSessionId() + "\",\"entryCode\":\"" + gameState.getEntryCode() + "\",\"presidentId\":\"" + gameState.getPresidentId() + "\",\"createdAt\":\"" + gameState.getCreatedAt() + "\",\"players\":" + objectMapper.writeValueAsString(gameState.getPlayers()) + ",\"currentPlayerIndex\":" + gameState.getCurrentPlayerIndex() + ",\"isClockWise\":" + gameState.isClockWise() + ",\"firstCoinState\":\"" + gameState.getFirstCoinState() + "\",\"secondCoinState\":\"" + gameState.getSecondCoinState() + "\",\"currentPlayer\":" + objectMapper.writeValueAsString(gameState.getCurrentPlayer()) + ",\"isMyTurn\":" + gameState.isMyTurn() + ",\"isPresident\":" + gameState.isPresident() + ",\"gameState\":\"" + gameState.getGameState() + "\",\"gameEndTime\":\"" + gameState.getGameEndTime() + "\",\"isLeeSoonSinByTimeExpired\":" + gameState.getLeeSoonSinByTimeExpired() + "}";
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("JSON 변환 오류: {}", e.getMessage());
            return createErrorResponse("INTERNAL_SERVER_ERROR", "JSON 변환 오류: " + e.getMessage());
        }
    }
    
    // 에러 응답 생성 (에러 코드 기반 구조)
    private String createErrorResponse(String errorCode, String message) {
        return "{\"type\":\"" + MSG_TYPE_ERROR + "\",\"errorCode\":\"" + errorCode + "\"}";
    }
    
    // 사용자 매핑 등록
    private void registerUserMapping(String sessionId, String userId) {
        sessionToUserMap.put(sessionId, userId);
        userToSessionMap.put(userId, sessionId);
        log.info("사용자 매핑 등록 - sessionId: {}, userId: {}", sessionId, userId);
    }
    
    // 게임 세션 매핑 등록 (새로 추가)
    private void registerGameSessionMapping(String webSocketSessionId, String gameSessionId) {
        sessionToGameSessionMap.put(webSocketSessionId, gameSessionId);
        gameSessionToWebSocketSessionsMap.computeIfAbsent(gameSessionId, k -> ConcurrentHashMap.newKeySet()).add(webSocketSessionId);
        log.info("게임 세션 매핑 등록 - webSocketSessionId: {}, gameSessionId: {}", webSocketSessionId, gameSessionId);
    }
    
    // 사용자 연결 끊김 처리
    private void handleUserDisconnection(String sessionId) {
        String userId = sessionToUserMap.get(sessionId);
        String gameSessionId = sessionToGameSessionMap.get(sessionId);
        
        if (userId != null) {
            log.info("사용자 연결 끊김 감지 - sessionId: {}, userId: {}", sessionId, userId);
            
            // 매핑 제거
            sessionToUserMap.remove(sessionId);
            userToSessionMap.remove(userId);
            
            // 게임 세션 매핑 제거 (새로 추가)
            if (gameSessionId != null) {
                sessionToGameSessionMap.remove(sessionId);
                Set<String> webSocketSessions = gameSessionToWebSocketSessionsMap.get(gameSessionId);
                if (webSocketSessions != null) {
                    webSocketSessions.remove(sessionId);
                    if (webSocketSessions.isEmpty()) {
                        gameSessionToWebSocketSessionsMap.remove(gameSessionId);
                    }
                }
            }
            
            // SessionService에서 플레이어 연결 끊김 처리
            // 모든 세션에서 해당 사용자가 있는지 확인하고 처리
            sessionService.handlePlayerDisconnectionByUserId(userId);
        }
    }
} 