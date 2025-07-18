package com.eos.lss.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SimpleWebSocketHandler extends TextWebSocketHandler {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("=== 일반 WebSocket 연결 성공 ===");
        log.info("세션 ID: {}", session.getId());
        log.info("원격 주소: {}", session.getRemoteAddress());
        
        sessions.put(session.getId(), session);
        
        // 연결 성공 메시지 전송
        session.sendMessage(new TextMessage("{\"type\":\"connection\",\"status\":\"connected\",\"sessionId\":\"" + session.getId() + "\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("=== 일반 WebSocket 메시지 수신 ===");
        log.info("세션 ID: {}", session.getId());
        log.info("메시지: {}", message.getPayload());
        
        // 에코 응답
        String response = "{\"type\":\"echo\",\"originalMessage\":" + message.getPayload() + ",\"timestamp\":\"" + System.currentTimeMillis() + "\"}";
        session.sendMessage(new TextMessage(response));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("=== 일반 WebSocket 연결 종료 ===");
        log.info("세션 ID: {}", session.getId());
        log.info("종료 상태: {}", status);
        
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("=== 일반 WebSocket 전송 오류 ===");
        log.error("세션 ID: {}", session.getId());
        log.error("오류: {}", exception.getMessage(), exception);
        
        sessions.remove(session.getId());
    }

    // 모든 세션에 브로드캐스트
    public void broadcastToAll(String message) {
        sessions.values().forEach(session -> {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("브로드캐스트 오류: {}", e.getMessage());
            }
        });
    }

    // 특정 세션에 메시지 전송
    public void sendToSession(String sessionId, String message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("세션 메시지 전송 오류: {}", e.getMessage());
            }
        }
    }
} 