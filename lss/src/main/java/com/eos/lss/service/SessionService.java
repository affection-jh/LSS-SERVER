package com.eos.lss.service;

import com.eos.lss.dto.GameStateDto;
import com.eos.lss.dto.PlayerDto;
import com.eos.lss.entity.Session;
import com.eos.lss.entity.CoinState;
import com.eos.lss.websocket.SimpleWebSocketHandler;
import com.eos.lss.exception.SessionNotFoundException;
import com.eos.lss.exception.InvalidGameStateException;
import com.eos.lss.exception.PlayerAlreadyJoinedException;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.eos.lss.dto.GameErrorDto;

@Service
@Slf4j
public class SessionService {

    // 메모리 기반 세션 저장소
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final SimpleWebSocketHandler webSocketHandler;
    private final GameTimerService gameTimerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SessionService(SimpleWebSocketHandler webSocketHandler, 
                         @Lazy GameTimerService gameTimerService) {
        this.webSocketHandler = webSocketHandler;
        this.gameTimerService = gameTimerService;
    }

    public String createSession(String userId, String name) {
        // 세션 생성
        String sessionId = UUID.randomUUID().toString();
        String entryCode = generateEntryCode();
        
        // 플레이어 DTO 생성
        PlayerDto player = new PlayerDto(userId, name, null);
        
        Session session = new Session();
        session.setId(sessionId);
        session.setEntryCode(entryCode);
        session.setPresidentId(userId);
        session.setCreatedAt(LocalDateTime.now());
        session.setGameState(GameStateDto.STATE_WAITING_ROOM);
        session.setPlayers(Arrays.asList(player));
        session.setOrderedPlayers(new ArrayList<>());
        session.setCurrentPlayerIndex(0);
        session.setClockWise(true);
        session.setFirstCoinState(null);
        session.setSecondCoinState(null);
        
        // 메모리에 저장
        sessions.put(sessionId, session);
        
        // WebSocket으로 게임 상태 브로드캐스트 (특정 게임 세션에만)
        GameStateDto gameState = convertToGameStateDto(session, userId);
        try {
            String broadcastMessage = "{\"type\":\"ok\",\"sessionId\":\"" + sessionId + "\",\"entryCode\":\"" + gameState.getEntryCode() + "\",\"presidentId\":\"" + gameState.getPresidentId() + "\",\"createdAt\":\"" + gameState.getCreatedAt() + "\",\"players\":" + objectMapper.writeValueAsString(gameState.getPlayers()) + ",\"currentPlayerIndex\":" + gameState.getCurrentPlayerIndex() + ",\"isClockWise\":" + gameState.isClockWise() + ",\"firstCoinState\":\"" + gameState.getFirstCoinState() + "\",\"secondCoinState\":\"" + gameState.getSecondCoinState() + "\",\"currentPlayer\":" + objectMapper.writeValueAsString(gameState.getCurrentPlayer()) + ",\"isMyTurn\":" + gameState.isMyTurn() + ",\"isPresident\":" + gameState.isPresident() + ",\"gameState\":\"" + gameState.getGameState() + "\",\"gameEndTime\":\"" + gameState.getGameEndTime() + "\",\"isLeeSoonSinByTimeExpired\":" + gameState.getLeeSoonSinByTimeExpired() + "}";
            webSocketHandler.broadcastToGameSession(sessionId, broadcastMessage);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_INTERNAL_SERVER_ERROR + "\"}";
            webSocketHandler.broadcastToGameSession(sessionId, errorMsg);
        }
        
        return sessionId;
    }

    public String joinSession(String entryCode, String userId, String name) {
        Session session = sessions.values().stream()
                .filter(s -> s.getEntryCode().equals(entryCode))
                .findFirst()
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다."));
        
        if (!GameStateDto.STATE_WAITING_ROOM.equals(session.getGameState())) {
            throw new InvalidGameStateException("이미 진행중인 세션입니다.");
        }
        
        // 플레이어 DTO 생성
        PlayerDto player = new PlayerDto(userId, name, null);
        
        // 이미 참여한 플레이어인지 확인
        boolean alreadyJoined = session.getPlayers().stream()
                .anyMatch(p -> p.getUserId().equals(userId));
        if (alreadyJoined) {
            throw new PlayerAlreadyJoinedException("이미 참여한 플레이어입니다.");
        }
        
        // 플레이어 추가
        List<PlayerDto> updatedPlayers = new ArrayList<>(session.getPlayers());
        updatedPlayers.add(player);
        session.setPlayers(updatedPlayers);
        
        // 메모리에 저장
        sessions.put(session.getId(), session);
        
        // WebSocket으로 게임 상태 브로드캐스트 (특정 게임 세션에만)
        GameStateDto gameState = convertToGameStateDto(session, userId);
        try {
            String broadcastMessage = "{\"type\":\"ok\",\"sessionId\":\"" + session.getId() + "\",\"entryCode\":\"" + gameState.getEntryCode() + "\",\"presidentId\":\"" + gameState.getPresidentId() + "\",\"createdAt\":\"" + gameState.getCreatedAt() + "\",\"players\":" + objectMapper.writeValueAsString(gameState.getPlayers()) + ",\"currentPlayerIndex\":" + gameState.getCurrentPlayerIndex() + ",\"isClockWise\":" + gameState.isClockWise() + ",\"firstCoinState\":\"" + gameState.getFirstCoinState() + "\",\"secondCoinState\":\"" + gameState.getSecondCoinState() + "\",\"currentPlayer\":" + objectMapper.writeValueAsString(gameState.getCurrentPlayer()) + ",\"isMyTurn\":" + gameState.isMyTurn() + ",\"isPresident\":" + gameState.isPresident() + ",\"gameState\":\"" + gameState.getGameState() + "\",\"gameEndTime\":\"" + gameState.getGameEndTime() + "\",\"isLeeSoonSinByTimeExpired\":" + gameState.getLeeSoonSinByTimeExpired() + "}";
            webSocketHandler.broadcastToGameSession(session.getId(), broadcastMessage);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_INTERNAL_SERVER_ERROR + "\"}";
            webSocketHandler.broadcastToGameSession(session.getId(), errorMsg);
        }
        
        return session.getId();
    }

    public void leaveSession(String sessionId, String userId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException("세션을 찾을 수 없습니다.");
        }
        
        boolean isPresident = session.getPresidentId().equals(userId);
        boolean isMyTurn = false;
        int removedPlayerIndex = -1;
        
        // 현재 턴인지 확인
        if (!session.getOrderedPlayers().isEmpty() && 
            session.getCurrentPlayerIndex() < session.getOrderedPlayers().size()) {
            String currentPlayerId = session.getOrderedPlayers().get(session.getCurrentPlayerIndex()).getUserId();
            isMyTurn = userId.equals(currentPlayerId);
        }
        
        // 플레이어 제거
        List<PlayerDto> updatedPlayers = session.getPlayers().stream()
                .filter(player -> !player.getUserId().equals(userId))
                .collect(Collectors.toList());
        
        // orderedPlayers에서도 제거하고 제거된 인덱스 확인
        List<PlayerDto> updatedOrderedPlayers = session.getOrderedPlayers().stream()
                .filter(player -> !player.getUserId().equals(userId))
                .collect(Collectors.toList());
        
        // 1명 이하 남으면 세션 종료
        if (updatedPlayers.size() <= 1) {
            sessions.remove(sessionId);
            return;
        }
        
        // 방장이 나가는 경우 세션 종료
        if (isPresident) {
            sessions.remove(sessionId);
            return;
        }
        
        // currentPlayerIndex 조정
        int newCurrentPlayerIndex = session.getCurrentPlayerIndex();
        
        if (isMyTurn) {
            // 자기 차례일 때는 다음 턴으로 넘김
            if (session.isClockWise()) {
                newCurrentPlayerIndex = (session.getCurrentPlayerIndex() + 1) % updatedOrderedPlayers.size();
            } else {
                newCurrentPlayerIndex = (session.getCurrentPlayerIndex() - 1 + updatedOrderedPlayers.size()) % updatedOrderedPlayers.size();
            }
        }
        
        // 제거된 플레이어의 인덱스보다 뒤에 있으면 -1 조정
        if (removedPlayerIndex != -1 && newCurrentPlayerIndex > removedPlayerIndex) {
            newCurrentPlayerIndex--;
        }
        
        // 인덱스 범위 체크
        if (newCurrentPlayerIndex >= updatedOrderedPlayers.size()) {
            newCurrentPlayerIndex = 0;
        }
        
        session.setPlayers(updatedPlayers);
        session.setOrderedPlayers(updatedOrderedPlayers);
        session.setCurrentPlayerIndex(newCurrentPlayerIndex);
        
        sessions.put(sessionId, session);
    }

    // 턴 스킵 처리 (응답 없는 플레이어 자동 제거)
    public void skipTurn(String sessionId, String userId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException("세션을 찾을 수 없습니다.");
        }
        
        // 현재 턴인 플레이어인지 확인
        if (session.getOrderedPlayers().isEmpty() || 
            session.getCurrentPlayerIndex() >= session.getOrderedPlayers().size()) {
            return; // 게임이 진행중이 아님
        }
        
        String currentPlayerId = session.getOrderedPlayers().get(session.getCurrentPlayerIndex()).getUserId();
        if (!userId.equals(currentPlayerId)) {
            return; // 현재 턴 플레이어가 아님
        }
        
        // 스킵된 플레이어 정보 저장
        String skippedUserName = session.getOrderedPlayers().get(session.getCurrentPlayerIndex()).getName();
        
        // 플레이어 제거 (leaveSession 로직과 동일)
        List<PlayerDto> updatedPlayers = session.getPlayers().stream()
                .filter(player -> !player.getUserId().equals(userId))
                .collect(Collectors.toList());
        
        List<PlayerDto> updatedOrderedPlayers = session.getOrderedPlayers().stream()
                .filter(player -> !player.getUserId().equals(userId))
                .collect(Collectors.toList());
        
        // 1명 이하 남으면 세션 종료
        if (updatedPlayers.size() <= 1) {
            sessions.remove(sessionId);
            return;
        }
        
        // 방장이 스킵된 경우 세션 종료
        if (session.getPresidentId().equals(userId)) {
            sessions.remove(sessionId);
            return;
        }
        
        // 다음 턴으로 이동
        int nextIndex;
        if (session.isClockWise()) {
            nextIndex = (session.getCurrentPlayerIndex() + 1) % updatedOrderedPlayers.size();
        } else {
            nextIndex = (session.getCurrentPlayerIndex() - 1 + updatedOrderedPlayers.size()) % updatedOrderedPlayers.size();
        }
        
        session.setPlayers(updatedPlayers);
        session.setOrderedPlayers(updatedOrderedPlayers);
        session.setCurrentPlayerIndex(nextIndex);
        session.setFirstCoinState(null);
        session.setSecondCoinState(null);
        
        sessions.put(sessionId, session);
        
        // 업데이트된 게임 상태만 전송 (수동 턴 스킵은 에러 메시지 없음)
        GameStateDto updatedState = convertToGameStateDto(session, null);
        try {
            String broadcastMessage = "{\"type\":\"ok\",\"sessionId\":\"" + sessionId + "\",\"entryCode\":\"" + updatedState.getEntryCode() + "\",\"presidentId\":\"" + updatedState.getPresidentId() + "\",\"createdAt\":\"" + updatedState.getCreatedAt() + "\",\"players\":" + objectMapper.writeValueAsString(updatedState.getPlayers()) + ",\"currentPlayerIndex\":" + updatedState.getCurrentPlayerIndex() + ",\"isClockWise\":" + updatedState.isClockWise() + ",\"firstCoinState\":\"" + updatedState.getFirstCoinState() + "\",\"secondCoinState\":\"" + updatedState.getSecondCoinState() + "\",\"currentPlayer\":" + objectMapper.writeValueAsString(updatedState.getCurrentPlayer()) + ",\"isMyTurn\":" + updatedState.isMyTurn() + ",\"isPresident\":" + updatedState.isPresident() + ",\"gameState\":\"" + updatedState.getGameState() + "\",\"gameEndTime\":\"" + updatedState.getGameEndTime() + "\",\"isLeeSoonSinByTimeExpired\":" + updatedState.getLeeSoonSinByTimeExpired() + "}";
            webSocketHandler.broadcastToGameSession(sessionId, broadcastMessage);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_INTERNAL_SERVER_ERROR + "\"}";
            webSocketHandler.broadcastToGameSession(sessionId, errorMsg);
        }
    }

    public void deleteSession(String sessionId, String userId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException("세션을 찾을 수 없습니다.");
        }
        
        // 방장인지 확인
        if (!session.getPresidentId().equals(userId)) {
            throw new IllegalArgumentException("방장만 세션을 삭제할 수 있습니다.");
        }
        
        // 게임 타이머 취소
        gameTimerService.cancelGameTimer(sessionId);
        
        sessions.remove(sessionId);
    }

    public void startGame(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException("세션을 찾을 수 없습니다.");
        }
        
        session.setGameState(GameStateDto.STATE_ORDERING);
        sessions.put(sessionId, session);
        
        // 모든 플레이어에게 게임 상태 변경 브로드캐스트
        GameStateDto gameState = convertToGameStateDto(session, null);
        try {
            String broadcastMessage = "{\"type\":\"ok\",\"sessionId\":\"" + sessionId + "\",\"entryCode\":\"" + gameState.getEntryCode() + "\",\"presidentId\":\"" + gameState.getPresidentId() + "\",\"createdAt\":\"" + gameState.getCreatedAt() + "\",\"players\":" + objectMapper.writeValueAsString(gameState.getPlayers()) + ",\"currentPlayerIndex\":" + gameState.getCurrentPlayerIndex() + ",\"isClockWise\":" + gameState.isClockWise() + ",\"firstCoinState\":\"" + gameState.getFirstCoinState() + "\",\"secondCoinState\":\"" + gameState.getSecondCoinState() + "\",\"currentPlayer\":" + objectMapper.writeValueAsString(gameState.getCurrentPlayer()) + ",\"isMyTurn\":" + gameState.isMyTurn() + ",\"isPresident\":" + gameState.isPresident() + ",\"gameState\":\"" + gameState.getGameState() + "\",\"gameEndTime\":\"" + gameState.getGameEndTime() + "\",\"isLeeSoonSinByTimeExpired\":" + gameState.getLeeSoonSinByTimeExpired() + "}";
            webSocketHandler.broadcastToGameSession(sessionId, broadcastMessage);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_INTERNAL_SERVER_ERROR + "\"}";
            webSocketHandler.broadcastToGameSession(sessionId, errorMsg);
        }
    }

    public void registerOrder(String sessionId, String userId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException("세션을 찾을 수 없습니다.");
        }
        
        // 게임이 이미 시작된 경우 순서 등록 불가
        if (GameStateDto.STATE_ON_GOING.equals(session.getGameState()) || 
            GameStateDto.STATE_LEE_SOON_SIN.equals(session.getGameState())) {
            throw new InvalidGameStateException("게임이 이미 시작되어 순서 등록이 불가능합니다.");
        }
        
        // 이미 순서에 등록된 플레이어인지 확인
        boolean alreadyOrdered = session.getOrderedPlayers().stream()
                .anyMatch(player -> player.getUserId().equals(userId));
        
        if (!alreadyOrdered) {
            // 플레이어를 찾아서 순서에 추가
            PlayerDto player = session.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("플레이어를 찾을 수 없습니다."));
            
            List<PlayerDto> updatedOrderedPlayers = new ArrayList<>(session.getOrderedPlayers());
            updatedOrderedPlayers.add(player);
            session.setOrderedPlayers(updatedOrderedPlayers);
            sessions.put(sessionId, session);
            
            log.info("순서 등록 완료: {} ({})", player.getName(), player.getUserId());
            
            // 모든 플레이어에게 순서 등록 상태 브로드캐스트
            GameStateDto gameState = convertToGameStateDto(session, null);
            try {
                String broadcastMessage = "{\"type\":\"ok\",\"sessionId\":\"" + sessionId + "\",\"entryCode\":\"" + gameState.getEntryCode() + "\",\"presidentId\":\"" + gameState.getPresidentId() + "\",\"createdAt\":\"" + gameState.getCreatedAt() + "\",\"players\":" + objectMapper.writeValueAsString(gameState.getPlayers()) + ",\"currentPlayerIndex\":" + gameState.getCurrentPlayerIndex() + ",\"isClockWise\":" + gameState.isClockWise() + ",\"firstCoinState\":\"" + gameState.getFirstCoinState() + "\",\"secondCoinState\":\"" + gameState.getSecondCoinState() + "\",\"currentPlayer\":" + objectMapper.writeValueAsString(gameState.getCurrentPlayer()) + ",\"isMyTurn\":" + gameState.isMyTurn() + ",\"isPresident\":" + gameState.isPresident() + ",\"gameState\":\"" + gameState.getGameState() + "\",\"gameEndTime\":\"" + gameState.getGameEndTime() + "\",\"isLeeSoonSinByTimeExpired\":" + gameState.getLeeSoonSinByTimeExpired() + "}";
                webSocketHandler.broadcastToGameSession(sessionId, broadcastMessage);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_INTERNAL_SERVER_ERROR + "\"}";
                webSocketHandler.broadcastToGameSession(sessionId, errorMsg);
            }
        }
    }

    public void startPlaying(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException("세션을 찾을 수 없습니다.");
        }
        
        // 순서가 등록된 플레이어가 있는지 확인
        if (session.getOrderedPlayers().isEmpty()) {
            throw new InvalidGameStateException("순서가 등록된 플레이어가 없습니다.");
        }
        
        // 최소 2명 이상의 플레이어가 순서에 등록되어 있는지 확인
        if (session.getOrderedPlayers().size() < 2) {
            throw new InvalidGameStateException("게임을 시작하려면 최소 2명 이상의 플레이어가 필요합니다.");
        }
        
        // 순서 등록을 안 한 플레이어들을 찾아서 에러 메시지 전송
        List<PlayerDto> unregisteredPlayers = session.getPlayers().stream()
                .filter(player -> session.getOrderedPlayers().stream()
                        .noneMatch(orderedPlayer -> orderedPlayer.getUserId().equals(player.getUserId())))
                .collect(Collectors.toList());
        
        log.info("=== 순서 등록 안 한 플레이어 처리 ===");
        log.info("순서 등록 안 한 플레이어 수: {}", unregisteredPlayers.size());
        for (PlayerDto player : unregisteredPlayers) {
            log.info("순서 등록 안 한 플레이어: {} ({})", player.getName(), player.getUserId());
        }
        
        // 순서 등록을 안 한 플레이어들에게만 에러 메시지 전송 (이미 게임에 참여한 플레이어들에게는 전송하지 않음)
        for (PlayerDto player : unregisteredPlayers) {
            String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_NOT_REGISTERED_PLAYER + "\"}";
            webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), errorMsg);
            log.info("순서 등록 안 한 플레이어에게 에러 전송: {} ({}) - {}", player.getName(), player.getUserId(), GameErrorDto.ERROR_NOT_REGISTERED_PLAYER);
        }
        
        // 순서 등록 안 한 플레이어들을 세션에서 제거 (게임에 참여하지 않도록)
        List<PlayerDto> updatedPlayers = session.getPlayers().stream()
                .filter(player -> session.getOrderedPlayers().stream()
                        .anyMatch(orderedPlayer -> orderedPlayer.getUserId().equals(player.getUserId())))
                .collect(Collectors.toList());
        
        session.setPlayers(updatedPlayers);
        sessions.put(sessionId, session);
        
        log.info("순서 등록 안 한 플레이어 제거 완료 - 남은 플레이어 수: {}", updatedPlayers.size());
        
        log.info("=== 게임 플레이 시작 ===");
        log.info("세션 ID: {}", sessionId);
        log.info("순서 등록된 플레이어 수: {}", session.getOrderedPlayers().size());
        log.info("순서 등록된 플레이어들: {}", session.getOrderedPlayers().stream()
                .map(p -> p.getName() + "(" + p.getUserId() + ")")
                .collect(Collectors.joining(", ")));
        
        session.setGameState(GameStateDto.STATE_ON_GOING);
        session.setCurrentPlayerIndex(0);
        session.setClockWise(true);
        session.setFirstCoinState(null);
        session.setSecondCoinState(null);
        
        // 게임 마감 시간 설정 (순서 등록 후 10분)
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(10);
        session.setGameEndTime(endTime);
        
        sessions.put(sessionId, session);
        
        // 현재 턴 플레이어 정보 로깅
        if (!session.getOrderedPlayers().isEmpty()) {
            PlayerDto firstPlayer = session.getOrderedPlayers().get(0);
            log.info("첫 번째 턴 플레이어: {} ({})", firstPlayer.getName(), firstPlayer.getUserId());
        }
        
        // 정확한 타이머 설정
        try {
            gameTimerService.scheduleGameEnd(sessionId, endTime);
        } catch (Exception e) {
            log.error("게임 타이머 설정 중 오류 발생: {}", e.getMessage(), e);
            // 타이머 설정 실패해도 게임은 계속 진행
        }
        
        // 각 플레이어에게 개별 게임 상태 전송 (isMyTurn이 올바르게 계산되도록)
        for (PlayerDto player : session.getOrderedPlayers()) {
            GameStateDto gameState = convertToGameStateDto(session, player.getUserId());
            try {
                String playerMessage = "{\"type\":\"ok\",\"sessionId\":\"" + sessionId + "\",\"entryCode\":\"" + gameState.getEntryCode() + "\",\"presidentId\":\"" + gameState.getPresidentId() + "\",\"createdAt\":\"" + gameState.getCreatedAt() + "\",\"players\":" + objectMapper.writeValueAsString(gameState.getPlayers()) + ",\"currentPlayerIndex\":" + gameState.getCurrentPlayerIndex() + ",\"isClockWise\":" + gameState.isClockWise() + ",\"firstCoinState\":\"" + gameState.getFirstCoinState() + "\",\"secondCoinState\":\"" + gameState.getSecondCoinState() + "\",\"currentPlayer\":" + objectMapper.writeValueAsString(gameState.getCurrentPlayer()) + ",\"isMyTurn\":" + gameState.isMyTurn() + ",\"isPresident\":" + gameState.isPresident() + ",\"gameState\":\"" + gameState.getGameState() + "\",\"gameEndTime\":\"" + gameState.getGameEndTime() + "\",\"isLeeSoonSinByTimeExpired\":" + gameState.getLeeSoonSinByTimeExpired() + "}";
                webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), playerMessage);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_INTERNAL_SERVER_ERROR + "\"}";
                webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), errorMsg);
            }
        }
    }

    public void setCoinState(String sessionId, String coinType, String state) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException("세션을 찾을 수 없습니다.");
        }
        
        log.info("=== 동전 상태 변경 시작 ===");
        log.info("세션 ID: {}", sessionId);
        log.info("동전 타입: {}", coinType);
        log.info("동전 상태: {}", state);
        log.info("현재 첫 번째 동전 상태: {}", session.getFirstCoinState());
        log.info("현재 두 번째 동전 상태: {}", session.getSecondCoinState());
        
        CoinState coinState = CoinState.valueOf(state);
        
        if ("first".equals(coinType)) {
            session.setFirstCoinState(coinState);
            log.info("첫 번째 동전 상태 변경: {} -> {}", session.getFirstCoinState(), coinState);
        } else if ("second".equals(coinType)) {
            session.setSecondCoinState(coinState);
            log.info("두 번째 동전 상태 변경: {} -> {}", session.getSecondCoinState(), coinState);
        }
        
        sessions.put(sessionId, session);
        
        log.info("=== 동전 상태 변경 후 ===");
        log.info("변경된 첫 번째 동전 상태: {}", session.getFirstCoinState());
        log.info("변경된 두 번째 동전 상태: {}", session.getSecondCoinState());
        log.info("이순신 조건 확인: {} && {}", session.getFirstCoinState() == CoinState.head, session.getSecondCoinState() == CoinState.head);
        
        // 각 동전 상태 변경 시마다 모든 플레이어에게 개별 메시지 전송
        for (PlayerDto player : session.getOrderedPlayers()) {
            GameStateDto updatedState = convertToGameStateDto(session, player.getUserId());
            try {
                String playerMessage = "{\"type\":\"ok\",\"sessionId\":\"" + sessionId + "\",\"entryCode\":\"" + updatedState.getEntryCode() + "\",\"presidentId\":\"" + updatedState.getPresidentId() + "\",\"createdAt\":\"" + updatedState.getCreatedAt() + "\",\"players\":" + objectMapper.writeValueAsString(updatedState.getPlayers()) + ",\"currentPlayerIndex\":" + updatedState.getCurrentPlayerIndex() + ",\"isClockWise\":" + updatedState.isClockWise() + ",\"firstCoinState\":\"" + updatedState.getFirstCoinState() + "\",\"secondCoinState\":\"" + updatedState.getSecondCoinState() + "\",\"currentPlayer\":" + objectMapper.writeValueAsString(updatedState.getCurrentPlayer()) + ",\"isMyTurn\":" + updatedState.isMyTurn() + ",\"isPresident\":" + updatedState.isPresident() + ",\"gameState\":\"" + updatedState.getGameState() + "\",\"gameEndTime\":\"" + updatedState.getGameEndTime() + "\",\"isLeeSoonSinByTimeExpired\":" + updatedState.getLeeSoonSinByTimeExpired() + "}";
                webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), playerMessage);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_INTERNAL_SERVER_ERROR + "\"}";
                webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), errorMsg);
            }
        }
        
        // 두 동전이 모두 앞면이 되면 즉시 이순신 상태로 전환
        log.info("=== 이순신 조건 확인 ===");
        log.info("첫 번째 동전이 head인가: {}", session.getFirstCoinState() == CoinState.head);
        log.info("두 번째 동전이 head인가: {}", session.getSecondCoinState() == CoinState.head);
        log.info("이순신 조건 만족 여부: {}", (session.getFirstCoinState() == CoinState.head && session.getSecondCoinState() == CoinState.head));
        
        if (session.getFirstCoinState() == CoinState.head && 
            session.getSecondCoinState() == CoinState.head) {
            
            log.info("=== 동전 결과로 인한 이순신 상태 전환 ===");
            log.info("세션 ID: {}", sessionId);
            log.info("현재 게임 마감 시간: {}", session.getGameEndTime());
            
            // 동전으로 인한 이순신 상태임을 명시 (시간 초과가 아님)
            session.setIsLeeSoonSinByTimeExpired(false);
            sessions.put(sessionId, session);
            
            log.info("동전 결과로 인한 이순신 상태 설정 완료 - 시간 초과 플래그: {}", session.getIsLeeSoonSinByTimeExpired());
            
            // 이순신 상태 전환을 위한 추가 브로드캐스트
            for (PlayerDto player : session.getOrderedPlayers()) {
                GameStateDto leeSoonSinState = convertToGameStateDto(session, player.getUserId());
                try {
                    String leeSoonSinMessage = "{\"type\":\"ok\",\"sessionId\":\"" + sessionId + "\",\"entryCode\":\"" + leeSoonSinState.getEntryCode() + "\",\"presidentId\":\"" + leeSoonSinState.getPresidentId() + "\",\"createdAt\":\"" + leeSoonSinState.getCreatedAt() + "\",\"players\":" + objectMapper.writeValueAsString(leeSoonSinState.getPlayers()) + ",\"currentPlayerIndex\":" + leeSoonSinState.getCurrentPlayerIndex() + ",\"isClockWise\":" + leeSoonSinState.isClockWise() + ",\"firstCoinState\":\"" + leeSoonSinState.getFirstCoinState() + "\",\"secondCoinState\":\"" + leeSoonSinState.getSecondCoinState() + "\",\"currentPlayer\":" + objectMapper.writeValueAsString(leeSoonSinState.getCurrentPlayer()) + ",\"isMyTurn\":" + leeSoonSinState.isMyTurn() + ",\"isPresident\":" + leeSoonSinState.isPresident() + ",\"gameState\":\"" + leeSoonSinState.getGameState() + "\",\"gameEndTime\":\"" + leeSoonSinState.getGameEndTime() + "\",\"isLeeSoonSinByTimeExpired\":" + leeSoonSinState.getLeeSoonSinByTimeExpired() + "}";
                    webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), leeSoonSinMessage);
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_INTERNAL_SERVER_ERROR + "\"}";
                    webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), errorMsg);
                }
            }
        }
    }

    public void nextTurn(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException("세션을 찾을 수 없습니다.");
        }
        
        log.info("=== 다음 턴 처리 ===");
        log.info("세션 ID: {}", sessionId);
        log.info("현재 플레이어 인덱스: {}", session.getCurrentPlayerIndex());
        log.info("순서 등록된 플레이어 수: {}", session.getOrderedPlayers().size());
        log.info("현재 시계 방향: {}", session.isClockWise());
        
        if (!session.getOrderedPlayers().isEmpty() && 
            session.getCurrentPlayerIndex() < session.getOrderedPlayers().size()) {
            PlayerDto currentPlayer = session.getOrderedPlayers().get(session.getCurrentPlayerIndex());
            log.info("현재 턴 플레이어: {} ({})", currentPlayer.getName(), currentPlayer.getUserId());
        }
        
        // 이순신 상태에서 nextTurn이 호출된 경우 새로운 타이머 설정
        boolean wasLeeSoonSinState = (session.getFirstCoinState() == CoinState.head && 
                                     session.getSecondCoinState() == CoinState.head);
        
        log.info("이순신 상태였는가: {}", wasLeeSoonSinState);
        log.info("현재 동전 상태 - 첫 번째: {}, 두 번째: {}", session.getFirstCoinState(), session.getSecondCoinState());
        log.info("이순신 시간 초과 플래그: {}", session.getIsLeeSoonSinByTimeExpired());
        log.info("현재 게임 마감 시간: {}", session.getGameEndTime());
        
        // 동전 상태 확인하여 게임 로직 처리
        if (session.getFirstCoinState() == CoinState.head && 
            session.getSecondCoinState() == CoinState.head) {
            log.info("이순신 상태 - 동전 상태 유지");
            // 이순신 화면으로 이동 - 상태는 그대로 유지
            // 실제 이순신 히스토리는 클라이언트에서 Firebase로 처리
        } else if (session.getFirstCoinState() == CoinState.tail && 
                   session.getSecondCoinState() == CoinState.tail) {
            log.info("순서 바꾸기 - 시계 방향 변경: {} -> {}", session.isClockWise(), !session.isClockWise());
            // 순서 바꾸기
            session.setClockWise(!session.isClockWise());
        }
        
        // 다음 턴으로 이동
        int nextIndex;
        if (session.isClockWise()) {
            nextIndex = (session.getCurrentPlayerIndex() + 1) % session.getOrderedPlayers().size();
            log.info("시계 방향으로 다음 턴: {} -> {}", session.getCurrentPlayerIndex(), nextIndex);
        } else {
            nextIndex = (session.getCurrentPlayerIndex() - 1 + session.getOrderedPlayers().size()) % session.getOrderedPlayers().size();
            log.info("반시계 방향으로 다음 턴: {} -> {}", session.getCurrentPlayerIndex(), nextIndex);
        }
        
        session.setCurrentPlayerIndex(nextIndex);
        session.setFirstCoinState(null);
        session.setSecondCoinState(null);
        
        if (!session.getOrderedPlayers().isEmpty() && 
            nextIndex < session.getOrderedPlayers().size()) {
            PlayerDto nextPlayer = session.getOrderedPlayers().get(nextIndex);
            log.info("다음 턴 플레이어: {} ({})", nextPlayer.getName(), nextPlayer.getUserId());
        }
        
        // 시간 초과로 인한 이순신 상태에서 nextTurn이 호출된 경우에만 새로운 타이머 설정
        boolean wasTimeExpiredLeeSoonSin = Boolean.TRUE.equals(session.getIsLeeSoonSinByTimeExpired());
        log.info("시간 초과로 인한 이순신 상태였는가: {}", wasTimeExpiredLeeSoonSin);
        
        if (wasLeeSoonSinState && wasTimeExpiredLeeSoonSin) {
            log.info("시간 초과로 인한 이순신 상태에서 턴 진행 - 새로운 타이머 설정");
            LocalDateTime newEndTime = LocalDateTime.now().plusMinutes(10);
            session.setGameEndTime(newEndTime);
            gameTimerService.scheduleGameEnd(sessionId, newEndTime);
            log.info("새로운 게임 마감 시간 설정: {}", newEndTime);
        } else {
            log.info("타이머 리셋 조건 불충족 - wasLeeSoonSinState: {}, wasTimeExpiredLeeSoonSin: {}", wasLeeSoonSinState, wasTimeExpiredLeeSoonSin);
        }
        
        // 이순신 상태 플래그 초기화 (타이머 재설정 후)
        session.setIsLeeSoonSinByTimeExpired(null);
        
        sessions.put(sessionId, session);
        
        // 각 플레이어에게 개별 게임 상태 전송 (isMyTurn이 올바르게 계산되도록)
        for (PlayerDto player : session.getOrderedPlayers()) {
            GameStateDto updatedState = convertToGameStateDto(session, player.getUserId());
            try {
                String playerMessage = "{\"type\":\"ok\",\"sessionId\":\"" + sessionId + "\",\"entryCode\":\"" + updatedState.getEntryCode() + "\",\"presidentId\":\"" + updatedState.getPresidentId() + "\",\"createdAt\":\"" + updatedState.getCreatedAt() + "\",\"players\":" + objectMapper.writeValueAsString(updatedState.getPlayers()) + ",\"currentPlayerIndex\":" + updatedState.getCurrentPlayerIndex() + ",\"isClockWise\":" + updatedState.isClockWise() + ",\"firstCoinState\":\"" + updatedState.getFirstCoinState() + "\",\"secondCoinState\":\"" + updatedState.getSecondCoinState() + "\",\"currentPlayer\":" + objectMapper.writeValueAsString(updatedState.getCurrentPlayer()) + ",\"isMyTurn\":" + updatedState.isMyTurn() + ",\"isPresident\":" + updatedState.isPresident() + ",\"gameState\":\"" + updatedState.getGameState() + "\",\"gameEndTime\":\"" + updatedState.getGameEndTime() + "\",\"isLeeSoonSinByTimeExpired\":" + updatedState.getLeeSoonSinByTimeExpired() + "}";
                webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), playerMessage);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_INTERNAL_SERVER_ERROR + "\"}";
                webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), errorMsg);
            }
        }
    }

    public void continueFromLeeSoonSin(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException("세션을 찾을 수 없습니다.");
        }
        
        log.info("=== 이순신 계속하기 처리 ===");
        log.info("세션 ID: {}", sessionId);
        log.info("현재 동전 상태 - 첫 번째: {}, 두 번째: {}", session.getFirstCoinState(), session.getSecondCoinState());
        log.info("이순신 시간 초과 플래그: {}", session.getIsLeeSoonSinByTimeExpired());
        log.info("현재 게임 마감 시간: {}", session.getGameEndTime());
        
        // 시간 초과로 인한 이순신 상태였는지 먼저 확인 (동전 상태 초기화 전에)
        boolean wasTimeExpiredLeeSoonSin = Boolean.TRUE.equals(session.getIsLeeSoonSinByTimeExpired());
        
        log.info("시간 초과로 인한 이순신 상태였는가: {}", wasTimeExpiredLeeSoonSin);
        
        // 이순신 화면에서 계속하기 - 동전 상태 초기화하고 다음 턴으로
        session.setFirstCoinState(null);
        session.setSecondCoinState(null);
        
        // 다음 턴으로 이동
        int nextIndex;
        if (session.isClockWise()) {
            nextIndex = (session.getCurrentPlayerIndex() + 1) % session.getOrderedPlayers().size();
        } else {
            nextIndex = (session.getCurrentPlayerIndex() - 1 + session.getOrderedPlayers().size()) % session.getOrderedPlayers().size();
        }
        
        session.setCurrentPlayerIndex(nextIndex);
        
        // 시간 초과로 인한 이순신 상태였을 때만 타이머 리셋
        if (wasTimeExpiredLeeSoonSin) {
            log.info("시간 초과로 인한 이순신 상태에서 계속하기 - 새로운 타이머 설정");
            LocalDateTime newEndTime = LocalDateTime.now().plusMinutes(10);
            session.setGameEndTime(newEndTime);
            gameTimerService.scheduleGameEnd(sessionId, newEndTime);
            log.info("새로운 게임 마감 시간 설정: {}", newEndTime);
        } else {
            log.info("동전 결과로 인한 이순신 상태에서 계속하기 - 기존 타이머 유지");
            log.info("기존 게임 마감 시간 유지: {}", session.getGameEndTime());
        }
        
        // 이순신 상태 플래그 초기화 (타이머 재설정 후)
        session.setIsLeeSoonSinByTimeExpired(null);
        
        sessions.put(sessionId, session);
        
        // 각 플레이어에게 개별 게임 상태 전송 (isMyTurn, isPresident가 올바르게 계산되도록)
        for (PlayerDto player : session.getOrderedPlayers()) {
            GameStateDto updatedState = convertToGameStateDto(session, player.getUserId());
            try {
                String playerMessage = "{\"type\":\"ok\",\"sessionId\":\"" + sessionId + "\",\"entryCode\":\"" + updatedState.getEntryCode() + "\",\"presidentId\":\"" + updatedState.getPresidentId() + "\",\"createdAt\":\"" + updatedState.getCreatedAt() + "\",\"players\":" + objectMapper.writeValueAsString(updatedState.getPlayers()) + ",\"currentPlayerIndex\":" + updatedState.getCurrentPlayerIndex() + ",\"isClockWise\":" + updatedState.isClockWise() + ",\"firstCoinState\":\"" + updatedState.getFirstCoinState() + "\",\"secondCoinState\":\"" + updatedState.getSecondCoinState() + "\",\"currentPlayer\":" + objectMapper.writeValueAsString(updatedState.getCurrentPlayer()) + ",\"isMyTurn\":" + updatedState.isMyTurn() + ",\"isPresident\":" + updatedState.isPresident() + ",\"gameState\":\"" + updatedState.getGameState() + "\",\"gameEndTime\":\"" + updatedState.getGameEndTime() + "\",\"isLeeSoonSinByTimeExpired\":" + updatedState.getLeeSoonSinByTimeExpired() + "}";
                webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), playerMessage);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_INTERNAL_SERVER_ERROR + "\"}";
                webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), errorMsg);
            }
        }
    }

    public GameStateDto getGameState(String sessionId, String userId) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException("세션을 찾을 수 없습니다.");
        }
        
        // 게임이 진행 중이거나 이순신 상태일 때, 순서에 등록되지 않은 플레이어는 접근 불가
        if ((GameStateDto.STATE_ON_GOING.equals(session.getGameState()) || 
             GameStateDto.STATE_LEE_SOON_SIN.equals(session.getGameState())) && 
            userId != null) {
            
            boolean isRegistered = session.getOrderedPlayers().stream()
                    .anyMatch(player -> player.getUserId().equals(userId));
            
            if (!isRegistered) {
                throw new InvalidGameStateException("순서에 등록되지 않은 플레이어는 게임에 참여할 수 없습니다.");
            }
        }
        
        return convertToGameStateDto(session, userId);
    }

    private GameStateDto convertToGameStateDto(Session session, String userId) {
        // 현재 턴 플레이어 ID
        String currentPlayerId = null;
        if (!session.getOrderedPlayers().isEmpty() && 
            session.getCurrentPlayerIndex() < session.getOrderedPlayers().size()) {
            currentPlayerId = session.getOrderedPlayers().get(session.getCurrentPlayerIndex()).getUserId();
        }
        
        // 턴 계산 디버깅 로그
        if (GameStateDto.STATE_ON_GOING.equals(session.getGameState()) || 
            GameStateDto.STATE_LEE_SOON_SIN.equals(session.getGameState())) {
            log.info("=== 턴 계산 디버깅 ===");
            log.info("세션 ID: {}", session.getId());
            log.info("게임 상태: {}", session.getGameState());
            log.info("순서 등록된 플레이어 수: {}", session.getOrderedPlayers().size());
            log.info("현재 플레이어 인덱스: {}", session.getCurrentPlayerIndex());
            log.info("시계 방향: {}", session.isClockWise());
            log.info("현재 턴 플레이어 ID: {}", currentPlayerId);
            log.info("요청한 사용자 ID: {}", userId);
            
            if (!session.getOrderedPlayers().isEmpty()) {
                log.info("순서 등록된 플레이어들:");
                for (int i = 0; i < session.getOrderedPlayers().size(); i++) {
                    PlayerDto player = session.getOrderedPlayers().get(i);
                    String marker = (i == session.getCurrentPlayerIndex()) ? " [현재 턴]" : "";
                    log.info("  {}: {} ({}){}", i, player.getName(), player.getUserId(), marker);
                }
            }
        }
        
        // 요청한 사용자의 턴인지 확인 (null 체크 추가)
        boolean isMyTurn = false;
        if (userId != null && currentPlayerId != null) {
            isMyTurn = userId.equals(currentPlayerId);
            if (GameStateDto.STATE_ON_GOING.equals(session.getGameState()) || 
                GameStateDto.STATE_LEE_SOON_SIN.equals(session.getGameState())) {
                log.info("사용자 {}의 턴 여부: {}", userId, isMyTurn);
            }
        }
        
        // 요청한 사용자가 방장인지 확인 (null 체크 추가)
        boolean isPresident = false;
        if (userId != null && session.getPresidentId() != null) {
            isPresident = userId.equals(session.getPresidentId());
            log.info("=== 방장 계산 디버깅 ===");
            log.info("요청한 사용자 ID: {}", userId);
            log.info("세션 방장 ID: {}", session.getPresidentId());
            log.info("방장 여부: {}", isPresident);
        } else {
            log.info("=== 방장 계산 디버깅 (null 체크 실패) ===");
            log.info("요청한 사용자 ID: {}", userId);
            log.info("세션 방장 ID: {}", session.getPresidentId());
        }
        
        // 게임 상태 결정
        String gameState = determineGameState(session);
        
        // Player 리스트를 PlayerDto 리스트로 변환
        List<PlayerDto> pendingPlayerDtos = session.getPlayers().stream()
                .map(player -> new PlayerDto(player.getUserId(), player.getName(), player.getProfileImageUrl()))
                .collect(Collectors.toList());
        
        List<PlayerDto> orderedPlayerDtos = session.getOrderedPlayers().stream()
                .map(player -> new PlayerDto(player.getUserId(), player.getName(), player.getProfileImageUrl()))
                .collect(Collectors.toList());
        
        GameStateDto dto = new GameStateDto();
        dto.setSessionId(session.getId());
        dto.setEntryCode(session.getEntryCode());
        dto.setPresidentId(session.getPresidentId());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setGameState(gameState);
        dto.setCurrentPlayerIndex(session.getCurrentPlayerIndex());
        dto.setClockWise(session.isClockWise());
        dto.setFirstCoinState(session.getFirstCoinState());
        dto.setSecondCoinState(session.getSecondCoinState());
        
        // 현재 턴 플레이어 정보 설정 (안전한 null 체크)
        PlayerDto currentPlayerDto = null;
        if (currentPlayerId != null && !session.getOrderedPlayers().isEmpty() && 
            session.getCurrentPlayerIndex() < session.getOrderedPlayers().size()) {
            PlayerDto currentPlayer = session.getOrderedPlayers().get(session.getCurrentPlayerIndex());
            currentPlayerDto = new PlayerDto(currentPlayer.getUserId(), currentPlayer.getName(), currentPlayer.getProfileImageUrl());
        }
        dto.setCurrentPlayer(currentPlayerDto);
        
        dto.setMyTurn(isMyTurn);
        dto.setPresident(isPresident);
        dto.setGameState(gameState);
        
        // 게임 마감 시간 정보 설정
        dto.setGameEndTime(session.getGameEndTime());
        dto.setLeeSoonSinByTimeExpired(session.getIsLeeSoonSinByTimeExpired());
        
        // 게임 상태별로 적절한 플레이어 리스트만 포함 (MUX 방식)
        switch (gameState) {
            case GameStateDto.STATE_WAITING_ROOM:
                // 대기실에서는 pending 플레이어 리스트
                dto.setPlayers(pendingPlayerDtos);
                break;
            case GameStateDto.STATE_ORDER_REGISTER:
                // 순서 등록에서는 순서가 정해진 플레이어 리스트
                dto.setPlayers(orderedPlayerDtos);
                break;
            case GameStateDto.STATE_GAME_PLAYING:
            case GameStateDto.STATE_LEE_SOON_SIN:
                // 게임 진행과 이순신에서는 순서 등록된 플레이어 리스트
                dto.setPlayers(orderedPlayerDtos);
                break;

            default:
                // 기본적으로는 pending 플레이어 리스트
                dto.setPlayers(pendingPlayerDtos);
        }
        
        return dto;
    }

    private String determineGameState(Session session) {
        // 이순신 조건 확인 (두 동전 모두 앞면)
        if (session.getFirstCoinState() == CoinState.head && 
            session.getSecondCoinState() == CoinState.head) {
            return GameStateDto.STATE_LEE_SOON_SIN;
        }
        
        // 세션 상태에 따른 화면 결정
        switch (session.getGameState()) {
            case GameStateDto.STATE_WAITING_ROOM:
                return GameStateDto.STATE_WAITING_ROOM;
            case GameStateDto.STATE_ORDERING:
                return GameStateDto.STATE_ORDER_REGISTER;
            case GameStateDto.STATE_ON_GOING:
                return GameStateDto.STATE_GAME_PLAYING;

            default:
                return GameStateDto.STATE_WAITING_ROOM;
        }
    }

    private String generateEntryCode() {
        while (true) {
            // 6자리 숫자 생성 (000000 ~ 999999)
            String entryCode = String.format("%06d", new Random().nextInt(1000000));
            
            // 중복 확인
            boolean isDuplicate = false;
            for (Session session : sessions.values()) {
                if (session.getEntryCode().equals(entryCode)) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (!isDuplicate) {
        return entryCode;
            }
        }
    }

    // 게임 마감 시간 체크 및 자동 이순신 상태 전환
    public void checkGameEndTime(String sessionId) {
        Session session = sessions.get(sessionId);
        
        if (session == null || session.getGameEndTime() == null) {
            return;
        }
        
        // 게임 마감 시간이 지났고, 현재 게임 진행 중인 경우
        if (LocalDateTime.now().isAfter(session.getGameEndTime()) && 
            GameStateDto.STATE_ON_GOING.equals(session.getGameState())) {
            
            // 이미 이순신 상태인 경우 처리하지 않음
            if (session.getFirstCoinState() == CoinState.head && 
                session.getSecondCoinState() == CoinState.head) {
                return;
            }
            
            log.info("=== 시간 초과로 인한 이순신 상태 전환 ===");
            log.info("세션 ID: {}", sessionId);
            log.info("현재 게임 마감 시간: {}", session.getGameEndTime());
            log.info("현재 시간: {}", LocalDateTime.now());
            
            // 이순신 상태로 강제 전환 (시간 초과로 인한 것임을 표시)
            session.setFirstCoinState(CoinState.head);
            session.setSecondCoinState(CoinState.head);
            session.setIsLeeSoonSinByTimeExpired(true);
            
            sessions.put(sessionId, session);
            
            log.info("시간 초과로 인한 이순신 상태 설정 완료 - 시간 초과 플래그: {}", session.getIsLeeSoonSinByTimeExpired());
            
            // 이순신 상태 전환 알림 전송
            String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_GAME_TIME_EXPIRED + "\"}";
            webSocketHandler.broadcastToGameSession(sessionId, errorMsg);
            
            // 각 플레이어에게 개별 게임 상태 전송 (isMyTurn, isPresident가 올바르게 계산되도록)
            for (PlayerDto player : session.getOrderedPlayers()) {
                GameStateDto updatedState = convertToGameStateDto(session, player.getUserId());
                try {
                    String playerMessage = "{\"type\":\"ok\",\"sessionId\":\"" + sessionId + "\",\"entryCode\":\"" + updatedState.getEntryCode() + "\",\"presidentId\":\"" + updatedState.getPresidentId() + "\",\"createdAt\":\"" + updatedState.getCreatedAt() + "\",\"players\":" + objectMapper.writeValueAsString(updatedState.getPlayers()) + ",\"currentPlayerIndex\":" + updatedState.getCurrentPlayerIndex() + ",\"isClockWise\":" + updatedState.isClockWise() + ",\"firstCoinState\":\"" + updatedState.getFirstCoinState() + "\",\"secondCoinState\":\"" + updatedState.getSecondCoinState() + "\",\"currentPlayer\":" + objectMapper.writeValueAsString(updatedState.getCurrentPlayer()) + ",\"isMyTurn\":" + updatedState.isMyTurn() + ",\"isPresident\":" + updatedState.isPresident() + ",\"gameState\":\"" + updatedState.getGameState() + "\",\"gameEndTime\":\"" + updatedState.getGameEndTime() + "\",\"isLeeSoonSinByTimeExpired\":" + updatedState.getLeeSoonSinByTimeExpired() + "}";
                    webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), playerMessage);
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    String jsonErrorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_INTERNAL_SERVER_ERROR + "\"}";
                    webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), jsonErrorMsg);
                }
            }
        }
    }
    
    // 사용자 ID로 플레이어 연결 끊김 처리 (모든 세션에서 검색)
    public void handlePlayerDisconnectionByUserId(String userId) {
        // 해당 사용자가 있는 세션을 찾아서 처리
        for (String sessionId : sessions.keySet()) {
            Session session = sessions.get(sessionId);
            if (session != null && session.getPlayers().stream()
                    .anyMatch(player -> player.getUserId().equals(userId))) {
                handlePlayerDisconnection(sessionId, userId);
                break; // 첫 번째로 찾은 세션에서만 처리
            }
        }
    }
    
    // 플레이어 연결 끊김 처리 (자동 턴 스킵 포함)
    public void handlePlayerDisconnection(String sessionId, String userId) {
        Session session = sessions.get(sessionId);
        
        if (session == null) {
            return; // 이미 삭제된 세션
        }
        
        log.info("=== 플레이어 연결 끊김 처리 시작 ===");
        log.info("세션 ID: {}", sessionId);
        log.info("연결 끊긴 사용자 ID: {}", userId);
        log.info("현재 방장 ID: {}", session.getPresidentId());
        log.info("현재 플레이어 수: {}", session.getPlayers().size());
        log.info("현재 순서 등록된 플레이어 수: {}", session.getOrderedPlayers().size());
        
        // 방장이 연결 끊긴 경우 세션 종료 (가장 먼저 체크)
        if (session.getPresidentId().equals(userId)) {
            log.info("방장이 연결 끊김 - 세션 종료");
            
            // 세션 삭제 전에 에러 메시지 전송
            String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_PRESIDENT_LEFT + "\"}";
            webSocketHandler.broadcastToGameSession(sessionId, errorMsg);
            
            // 세션 삭제
            sessions.remove(sessionId);
            return;
        }
        
        // 현재 턴 플레이어인지 확인
        boolean isCurrentTurnPlayer = false;
        if (!session.getOrderedPlayers().isEmpty() && 
            session.getCurrentPlayerIndex() < session.getOrderedPlayers().size()) {
            String currentPlayerId = session.getOrderedPlayers().get(session.getCurrentPlayerIndex()).getUserId();
            isCurrentTurnPlayer = userId.equals(currentPlayerId);
            log.info("현재 턴 플레이어인가: {}", isCurrentTurnPlayer);
        }
        
        // 플레이어 제거
        List<PlayerDto> updatedPlayers = session.getPlayers().stream()
                .filter(player -> !player.getUserId().equals(userId))
                .collect(Collectors.toList());
        
        List<PlayerDto> updatedOrderedPlayers = session.getOrderedPlayers().stream()
                .filter(player -> !player.getUserId().equals(userId))
                .collect(Collectors.toList());
        
        log.info("제거 후 플레이어 수: {}", updatedPlayers.size());
        log.info("제거 후 순서 등록된 플레이어 수: {}", updatedOrderedPlayers.size());
        
        // 1명 이하 남으면 세션 종료 (방장 체크 후에 실행)
        if (updatedPlayers.size() <= 1) {
            log.info("플레이어가 1명 이하로 남음 - 세션 종료");
            
            // 세션 삭제 전에 에러 메시지 전송
            String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_INSUFFICIENT_PLAYERS + "\"}";
            webSocketHandler.broadcastToGameSession(sessionId, errorMsg);
            
            // 세션 삭제
            sessions.remove(sessionId);
            return;
        }
        
        // 플레이어 제거 및 게임 상태 업데이트
        session.setPlayers(updatedPlayers);
        session.setOrderedPlayers(updatedOrderedPlayers);
        
        // 현재 턴 플레이어가 연결 끊어진 경우 자동 턴 스킵
        if (isCurrentTurnPlayer) {
            log.info("현재 턴 플레이어 연결 끊김 - 자동 턴 스킵");
            
            // 다음 턴으로 이동
            int nextIndex;
            if (session.isClockWise()) {
                nextIndex = (session.getCurrentPlayerIndex() + 1) % updatedOrderedPlayers.size();
            } else {
                nextIndex = (session.getCurrentPlayerIndex() - 1 + updatedOrderedPlayers.size()) % updatedOrderedPlayers.size();
            }
            
            session.setCurrentPlayerIndex(nextIndex);
            session.setFirstCoinState(null);
            session.setSecondCoinState(null);
            
            log.info("다음 턴 인덱스: {}", nextIndex);
            
            // 자동 턴 스킵 에러 메시지 전송
            String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_TURN_SKIPPED + "\"}";
            webSocketHandler.broadcastToGameSession(sessionId, errorMsg);
        } else {
            // 현재 턴 플레이어가 아닌 경우 인덱스 조정
            if (session.getCurrentPlayerIndex() >= updatedOrderedPlayers.size()) {
                session.setCurrentPlayerIndex(0);
                log.info("인덱스 조정: 0으로 설정");
            }
        }
        
        sessions.put(sessionId, session);
        
        // 플레이어 연결 끊김 에러 메시지 전송
        String errorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_PLAYER_DISCONNECTED + "\"}";
        webSocketHandler.broadcastToGameSession(sessionId, errorMsg);
        
        log.info("=== 플레이어 연결 끊김 처리 완료 ===");
        log.info("남은 플레이어 수: {}", session.getPlayers().size());
        log.info("남은 순서 등록된 플레이어 수: {}", session.getOrderedPlayers().size());
        log.info("현재 턴 인덱스: {}", session.getCurrentPlayerIndex());
        
        // 각 플레이어에게 개별 게임 상태 전송 (isMyTurn, isPresident가 올바르게 계산되도록)
        for (PlayerDto player : session.getOrderedPlayers()) {
            GameStateDto updatedState = convertToGameStateDto(session, player.getUserId());
            try {
                String playerMessage = "{\"type\":\"ok\",\"sessionId\":\"" + sessionId + "\",\"entryCode\":\"" + updatedState.getEntryCode() + "\",\"presidentId\":\"" + updatedState.getPresidentId() + "\",\"createdAt\":\"" + updatedState.getCreatedAt() + "\",\"players\":" + objectMapper.writeValueAsString(updatedState.getPlayers()) + ",\"currentPlayerIndex\":" + updatedState.getCurrentPlayerIndex() + ",\"isClockWise\":" + updatedState.isClockWise() + ",\"firstCoinState\":\"" + updatedState.getFirstCoinState() + "\",\"secondCoinState\":\"" + updatedState.getSecondCoinState() + "\",\"currentPlayer\":" + objectMapper.writeValueAsString(updatedState.getCurrentPlayer()) + ",\"isMyTurn\":" + updatedState.isMyTurn() + ",\"isPresident\":" + updatedState.isPresident() + ",\"gameState\":\"" + updatedState.getGameState() + "\",\"gameEndTime\":\"" + updatedState.getGameEndTime() + "\",\"isLeeSoonSinByTimeExpired\":" + updatedState.getLeeSoonSinByTimeExpired() + "}";
                webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), playerMessage);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                String jsonErrorMsg = "{\"type\":\"error\",\"errorCode\":\"" + GameErrorDto.ERROR_INTERNAL_SERVER_ERROR + "\"}";
                webSocketHandler.sendToUserInGameSession(sessionId, player.getUserId(), jsonErrorMsg);
            }
        }
    }
}


