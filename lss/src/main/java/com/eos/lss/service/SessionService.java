package com.eos.lss.service;

import com.eos.lss.dto.GameStateDto;
import com.eos.lss.dto.PlayerDto;
import com.eos.lss.entity.Session;
import com.eos.lss.entity.SessionStatus;
import com.eos.lss.entity.CoinState;
import com.eos.lss.entity.Player;
import com.eos.lss.repository.SessionRepository;
import com.eos.lss.repository.PlayerRepository;
import com.eos.lss.websocket.SimpleWebSocketHandler;
import com.eos.lss.exception.SessionNotFoundException;
import com.eos.lss.exception.InvalidGameStateException;
import com.eos.lss.exception.PlayerAlreadyJoinedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.eos.lss.dto.GameErrorDto;

@Service
@Transactional
public class SessionService {

    private final SessionRepository sessionRepository;
    private final PlayerRepository playerRepository;
    private final SimpleWebSocketHandler webSocketHandler;
    private final GameTimerService gameTimerService;

    public SessionService(SessionRepository sessionRepository, 
                         PlayerRepository playerRepository, 
                         SimpleWebSocketHandler webSocketHandler, 
                         @Lazy GameTimerService gameTimerService) {
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.webSocketHandler = webSocketHandler;
        this.gameTimerService = gameTimerService;
    }

    public String createSession(String userId, String name) {
        // 세션 생성
        String sessionId = UUID.randomUUID().toString();
        String entryCode = generateEntryCode();
        
        // 플레이어 생성 또는 조회
        Player player = playerRepository.findById(userId)
                .orElse(new Player(userId, name, null));
        if (player.getName() == null) {
            player.setName(name);
        }
        playerRepository.save(player);
        
        Session session = new Session();
        session.setId(sessionId);
        session.setEntryCode(entryCode);
        session.setPresidentId(userId);
        session.setCreatedAt(LocalDateTime.now());
        session.setStatus(SessionStatus.pending);
        session.setPlayers(Arrays.asList(player));
        session.setOrderedPlayers(new ArrayList<>());
        session.setCurrentPlayerIndex(0);
        session.setClockWise(true);
        session.setFirstCoinState(null);
        session.setSecondCoinState(null);
        
        Session savedSession = sessionRepository.save(session);
        
        // WebSocket으로 게임 상태 브로드캐스트
        GameStateDto gameState = convertToGameStateDto(savedSession, userId);
        webSocketHandler.broadcastToAll(gameState.toString());
        
        return sessionId;
    }

    public String joinSession(String entryCode, String userId, String name) {
        Session session = sessionRepository.findByEntryCode(entryCode)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다."));
        
        if (session.getStatus() != SessionStatus.pending) {
            throw new InvalidGameStateException("이미 진행중인 세션입니다.");
        }
        
        // 플레이어 생성 또는 조회
        Player player = playerRepository.findById(userId)
                .orElse(new Player(userId, name, null));
        if (player.getName() == null) {
            player.setName(name);
        }
        playerRepository.save(player);
        
        // 이미 참여한 플레이어인지 확인
        boolean alreadyJoined = session.getPlayers().stream()
                .anyMatch(p -> p.getUserId().equals(userId));
        if (alreadyJoined) {
            throw new PlayerAlreadyJoinedException("이미 참여한 플레이어입니다.");
        }
        
        // 플레이어 추가
        List<Player> updatedPlayers = new ArrayList<>(session.getPlayers());
        updatedPlayers.add(player);
        session.setPlayers(updatedPlayers);
        
        Session savedSession = sessionRepository.save(session);
        
        // WebSocket으로 게임 상태 브로드캐스트
        GameStateDto gameState = convertToGameStateDto(savedSession, userId);
        webSocketHandler.broadcastToAll(gameState.toString());
        
        return session.getId();
    }

    public void leaveSession(String sessionId, String userId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다."));
        
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
        List<Player> updatedPlayers = session.getPlayers().stream()
                .filter(player -> !player.getUserId().equals(userId))
                .collect(Collectors.toList());
        
        // orderedPlayers에서도 제거하고 제거된 인덱스 확인
        List<Player> updatedOrderedPlayers = new ArrayList<>();
        for (int i = 0; i < session.getOrderedPlayers().size(); i++) {
            Player player = session.getOrderedPlayers().get(i);
            if (!player.getUserId().equals(userId)) {
                updatedOrderedPlayers.add(player);
            } else {
                removedPlayerIndex = i;
            }
        }
        
        // 1명 이하 남으면 세션 종료
        if (updatedPlayers.size() <= 1) {
            session.setStatus(SessionStatus.finished);
            sessionRepository.save(session);
            sessionRepository.delete(session);
            return;
        }
        
        // 방장이 나가는 경우 세션 종료
        if (isPresident) {
            session.setStatus(SessionStatus.finished);
            sessionRepository.save(session);
            sessionRepository.delete(session);
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
        
        sessionRepository.save(session);
    }

    // 턴 스킵 처리 (응답 없는 플레이어 자동 제거)
    public void skipTurn(String sessionId, String userId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다."));
        
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
        List<Player> updatedPlayers = session.getPlayers().stream()
                .filter(player -> !player.getUserId().equals(userId))
                .collect(Collectors.toList());
        
        List<Player> updatedOrderedPlayers = new ArrayList<>();
        for (int i = 0; i < session.getOrderedPlayers().size(); i++) {
            Player player = session.getOrderedPlayers().get(i);
            if (!player.getUserId().equals(userId)) {
                updatedOrderedPlayers.add(player);
            }
        }
        
        // 1명 이하 남으면 세션 종료
        if (updatedPlayers.size() <= 1) {
            sessionRepository.delete(session);
            return;
        }
        
        // 방장이 스킵된 경우 세션 종료
        if (session.getPresidentId().equals(userId)) {
            sessionRepository.delete(session);
            return;
        }
        
        // 다음 턴으로 이동 (현재 턴이므로 자동으로 다음 턴)
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
        
        sessionRepository.save(session);
        
        // 업데이트된 게임 상태만 전송 (수동 턴 스킵은 에러 메시지 없음)
        GameStateDto updatedState = convertToGameStateDto(session, null);
        webSocketHandler.broadcastToAll(updatedState.toString());
    }
    


    public void deleteSession(String sessionId, String userId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다."));
        
        // 방장인지 확인
        if (!session.getPresidentId().equals(userId)) {
            throw new IllegalArgumentException("방장만 세션을 삭제할 수 있습니다.");
        }
        
        // 게임 타이머 취소
        gameTimerService.cancelGameTimer(sessionId);
        
        sessionRepository.delete(session);
    }

    public void startGame(String sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다."));
        
        session.setStatus(SessionStatus.ordering);
        sessionRepository.save(session);
    }

    public void registerOrder(String sessionId, String userId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다."));
        
        // 이미 순서에 등록된 플레이어인지 확인
        boolean alreadyOrdered = session.getOrderedPlayers().stream()
                .anyMatch(player -> player.getUserId().equals(userId));
        
        if (!alreadyOrdered) {
                    // 플레이어를 찾아서 순서에 추가
        Player player = session.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("플레이어를 찾을 수 없습니다."));
            
            List<Player> updatedOrderedPlayers = new ArrayList<>(session.getOrderedPlayers());
            updatedOrderedPlayers.add(player);
            session.setOrderedPlayers(updatedOrderedPlayers);
            sessionRepository.save(session);
        }
    }

    public void startPlaying(String sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다."));
        
        session.setStatus(SessionStatus.onGoing);
        session.setCurrentPlayerIndex(0);
        session.setClockWise(true);
        session.setFirstCoinState(null);
        session.setSecondCoinState(null);
        
        // 게임 마감 시간 설정 (순서 등록 후 10분)
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(10);
        session.setGameEndTime(endTime);
        
        sessionRepository.save(session);
        
        // 정확한 타이머 설정
        gameTimerService.scheduleGameEnd(sessionId, endTime);
    }

    public void setCoinState(String sessionId, String coinType, String state) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다."));
        
        CoinState coinState = CoinState.valueOf(state);
        
        if ("first".equals(coinType)) {
            session.setFirstCoinState(coinState);
        } else if ("second".equals(coinType)) {
            session.setSecondCoinState(coinState);
        }
        
        sessionRepository.save(session);
        
        // 두 동전이 모두 앞면이 되면 즉시 이순신 상태로 전환
        if (session.getFirstCoinState() == CoinState.head && 
            session.getSecondCoinState() == CoinState.head) {
            
            // 동전으로 인한 이순신 상태임을 명시 (시간 초과가 아님)
            session.setIsLeeSoonSinByTimeExpired(false);
            sessionRepository.save(session);
            
            // 업데이트된 게임 상태 전송 (이순신 상태로)
            GameStateDto updatedState = convertToGameStateDto(session, null);
            webSocketHandler.broadcastToAll(updatedState.toString());
        }
    }

    public void nextTurn(String sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다."));
        
        // 이순신 상태에서 nextTurn이 호출된 경우 새로운 타이머 설정
        boolean wasLeeSoonSinState = (session.getFirstCoinState() == CoinState.head && 
                                     session.getSecondCoinState() == CoinState.head);
        
        // 동전 상태 확인하여 게임 로직 처리
        if (session.getFirstCoinState() == CoinState.head && 
            session.getSecondCoinState() == CoinState.head) {
            // 이순신 화면으로 이동 - 상태는 그대로 유지
            // 실제 이순신 히스토리는 클라이언트에서 Firebase로 처리
        } else if (session.getFirstCoinState() == CoinState.tail && 
                   session.getSecondCoinState() == CoinState.tail) {
            // 순서 바꾸기
            session.setClockWise(!session.isClockWise());
        }
        
        // 다음 턴으로 이동
        int nextIndex;
        if (session.isClockWise()) {
            nextIndex = (session.getCurrentPlayerIndex() + 1) % session.getOrderedPlayers().size();
        } else {
            nextIndex = (session.getCurrentPlayerIndex() - 1 + session.getOrderedPlayers().size()) % session.getOrderedPlayers().size();
        }
        
        session.setCurrentPlayerIndex(nextIndex);
        session.setFirstCoinState(null);
        session.setSecondCoinState(null);
        
        // 시간 초과로 인한 이순신 상태에서 nextTurn이 호출된 경우에만 새로운 타이머 설정
        if (wasLeeSoonSinState && Boolean.TRUE.equals(session.getIsLeeSoonSinByTimeExpired())) {
            LocalDateTime newEndTime = LocalDateTime.now().plusMinutes(10);
            session.setGameEndTime(newEndTime);
            gameTimerService.scheduleGameEnd(sessionId, newEndTime);
        }
        
        // 이순신 상태 플래그 초기화
        session.setIsLeeSoonSinByTimeExpired(null);
        
        sessionRepository.save(session);
    }

    public void continueFromLeeSoonSin(String sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다."));
        
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
        if (Boolean.TRUE.equals(session.getIsLeeSoonSinByTimeExpired())) {
            LocalDateTime newEndTime = LocalDateTime.now().plusMinutes(10);
            session.setGameEndTime(newEndTime);
            gameTimerService.scheduleGameEnd(sessionId, newEndTime);
        }
        
        // 이순신 상태 플래그 초기화
        session.setIsLeeSoonSinByTimeExpired(null);
        
        sessionRepository.save(session);
    }

    public GameStateDto getGameState(String sessionId, String userId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다."));
        
        return convertToGameStateDto(session, userId);
    }

    private GameStateDto convertToGameStateDto(Session session, String userId) {
        // 현재 턴 플레이어 ID
        String currentPlayerId = null;
        if (!session.getOrderedPlayers().isEmpty() && 
            session.getCurrentPlayerIndex() < session.getOrderedPlayers().size()) {
            currentPlayerId = session.getOrderedPlayers().get(session.getCurrentPlayerIndex()).getUserId();
        }
        
        // 요청한 사용자의 턴인지 확인
        boolean isMyTurn = userId.equals(currentPlayerId);
        
        // 요청한 사용자가 방장인지 확인
        boolean isPresident = userId.equals(session.getPresidentId());
        
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
        dto.setStatus(session.getStatus());
        dto.setCurrentPlayerIndex(session.getCurrentPlayerIndex());
        dto.setClockWise(session.isClockWise());
        dto.setFirstCoinState(session.getFirstCoinState());
        dto.setSecondCoinState(session.getSecondCoinState());
        
        // 현재 턴 플레이어 정보 설정 (안전한 null 체크)
        PlayerDto currentPlayerDto = null;
        if (currentPlayerId != null && !session.getOrderedPlayers().isEmpty() && 
            session.getCurrentPlayerIndex() < session.getOrderedPlayers().size()) {
            Player currentPlayer = session.getOrderedPlayers().get(session.getCurrentPlayerIndex());
            currentPlayerDto = new PlayerDto(currentPlayer.getUserId(), currentPlayer.getName(), currentPlayer.getProfileImageUrl());
        }
        dto.setCurrentPlayer(currentPlayerDto);
        
        dto.setMyTurn(isMyTurn);
        dto.setPresident(isPresident);
        dto.setGameState(gameState);
        
        // 게임 마감 시간 정보 설정
        dto.setGameEndTime(session.getGameEndTime());
        dto.setIsLeeSoonSinByTimeExpired(session.getIsLeeSoonSinByTimeExpired());
        
        // 게임 상태별로 적절한 플레이어 리스트만 포함 (MUX 방식)
        switch (gameState) {
            case GameStateDto.STATE_WAITING_ROOM:
            case GameStateDto.STATE_ORDER_REGISTER:
                // 대기실과 순서 등록에서는 pending 플레이어 리스트
                dto.setPlayers(pendingPlayerDtos);
                break;
            case GameStateDto.STATE_GAME_PLAYING:
            case GameStateDto.STATE_LEE_SOON_SIN:
                // 게임 진행과 이순신에서는 순서 등록된 플레이어 리스트
                dto.setPlayers(orderedPlayerDtos);
                break;
            case GameStateDto.STATE_GAME_FINISHED:
                // 게임 종료에서는 빈 리스트
                dto.setPlayers(null);
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
        switch (session.getStatus()) {
            case pending:
                return GameStateDto.STATE_WAITING_ROOM;
            case ordering:
                return GameStateDto.STATE_ORDER_REGISTER;
            case onGoing:
                return GameStateDto.STATE_GAME_PLAYING;
            case finished:
                return GameStateDto.STATE_GAME_FINISHED;
            default:
                return GameStateDto.STATE_WAITING_ROOM;
        }
    }



    private String generateEntryCode() {
        String entryCode;
        do {
            // 6자리 숫자 생성 (000000 ~ 999999)
            entryCode = String.format("%06d", new Random().nextInt(1000000));
        } while (sessionRepository.findByEntryCode(entryCode).isPresent());
        
        return entryCode;
    }

    // 게임 마감 시간 체크 및 자동 이순신 상태 전환
    public void checkGameEndTime(String sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElse(null);
        
        if (session == null || session.getGameEndTime() == null) {
            return;
        }
        
        // 게임 마감 시간이 지났고, 현재 게임 진행 중인 경우
        if (LocalDateTime.now().isAfter(session.getGameEndTime()) && 
            session.getStatus() == SessionStatus.onGoing) {
            
            // 이미 이순신 상태인 경우 처리하지 않음
            if (session.getFirstCoinState() == CoinState.head && 
                session.getSecondCoinState() == CoinState.head) {
                return;
            }
            
            // 이순신 상태로 강제 전환 (시간 초과로 인한 것임을 표시)
            session.setFirstCoinState(CoinState.head);
            session.setSecondCoinState(CoinState.head);
            session.setIsLeeSoonSinByTimeExpired(true);
            
            sessionRepository.save(session);
            
            // 이순신 상태 전환 알림 전송
            GameErrorDto errorDto = new GameErrorDto(GameErrorDto.ERROR_GAME_TIME_EXPIRED);
            webSocketHandler.broadcastToAll(errorDto.toString());
            
            // 업데이트된 게임 상태 전송
            GameStateDto updatedState = convertToGameStateDto(session, null);
            webSocketHandler.broadcastToAll(updatedState.toString());
        }
    }
    
    // 플레이어 연결 끊김 처리 (자동 턴 스킵 포함)
    public void handlePlayerDisconnection(String sessionId, String userId) {
        Session session = sessionRepository.findById(sessionId)
                .orElse(null);
        
        if (session == null) {
            return; // 이미 삭제된 세션
        }
        
        // 현재 턴 플레이어인지 확인
        boolean isCurrentTurnPlayer = false;
        if (!session.getOrderedPlayers().isEmpty() && 
            session.getCurrentPlayerIndex() < session.getOrderedPlayers().size()) {
            String currentPlayerId = session.getOrderedPlayers().get(session.getCurrentPlayerIndex()).getUserId();
            isCurrentTurnPlayer = userId.equals(currentPlayerId);
        }
        
        // 플레이어 제거
        List<Player> updatedPlayers = session.getPlayers().stream()
                .filter(player -> !player.getUserId().equals(userId))
                .collect(Collectors.toList());
        
        List<Player> updatedOrderedPlayers = session.getOrderedPlayers().stream()
                .filter(player -> !player.getUserId().equals(userId))
                .collect(Collectors.toList());
        
        // 1명 이하 남으면 세션 종료
        if (updatedPlayers.size() <= 1) {
            sessionRepository.delete(session);
            
            // 에러 메시지 전송 (별도 topic으로)
            GameErrorDto errorDto = new GameErrorDto(GameErrorDto.ERROR_INSUFFICIENT_PLAYERS);
            webSocketHandler.broadcastToAll(errorDto.toString());
            return;
        }
        
        // 방장이 연결 끊긴 경우 세션 종료
        if (session.getPresidentId().equals(userId)) {
            sessionRepository.delete(session);
            
            // 에러 메시지 전송 (별도 topic으로)
            GameErrorDto errorDto = new GameErrorDto(GameErrorDto.ERROR_PRESIDENT_LEFT);
            webSocketHandler.broadcastToAll(errorDto.toString());
            return;
        }
        
        // 플레이어 제거 및 게임 상태 업데이트
        session.setPlayers(updatedPlayers);
        session.setOrderedPlayers(updatedOrderedPlayers);
        
        // 현재 턴 플레이어가 연결 끊어진 경우 자동 턴 스킵
        if (isCurrentTurnPlayer) {
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
            
            // 자동 턴 스킵 에러 메시지 전송 (별도 topic으로)
            GameErrorDto skipErrorDto = new GameErrorDto(GameErrorDto.ERROR_TURN_SKIPPED);
            webSocketHandler.broadcastToAll(skipErrorDto.toString());
        } else {
            // 현재 턴 플레이어가 아닌 경우 인덱스 조정
            if (session.getCurrentPlayerIndex() >= updatedOrderedPlayers.size()) {
                session.setCurrentPlayerIndex(0);
            }
        }
        
        sessionRepository.save(session);
        
        // 플레이어 연결 끊김 에러 메시지 전송 (별도 topic으로)
        GameErrorDto errorDto = new GameErrorDto(GameErrorDto.ERROR_PLAYER_DISCONNECTED);
        webSocketHandler.broadcastToAll(errorDto.toString());
        
        // 업데이트된 게임 상태도 전송
        GameStateDto updatedState = convertToGameStateDto(session, null);
        webSocketHandler.broadcastToAll(updatedState.toString());
    }


} 