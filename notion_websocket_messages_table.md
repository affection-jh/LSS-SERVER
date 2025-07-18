# 서버 웹소켓 메시지 명세 (노션용)

**최종 업데이트**: 2024년 1월
**버전**: 2.0

## 1. 정상 응답 메시지 (GameStateDto)

| Topic | 설명 | JSON 구조 |
|-------|------|-----------|
| `/topic/session-created` | 세션 생성 응답 | ```json<br/>{<br/>  "sessionId": "uuid-string",<br/>  "entryCode": "123456",<br/>  "presidentId": "user123",<br/>  "createdAt": "2024-01-01T12:00:00",<br/>  "status": "pending",<br/>  "players": [...],<br/>  "currentPlayerIndex": 0,<br/>  "isClockWise": true,<br/>  "firstCoinState": null,<br/>  "secondCoinState": null,<br/>  "currentPlayer": null,<br/>  "isMyTurn": false,<br/>  "isPresident": true,<br/>  "gameState": "WAITING_ROOM",<br/>  "gameEndTime": null<br/>}``` |
| `/topic/session-joined` | 세션 입장 응답 | ```json<br/>{<br/>  "sessionId": "uuid-string",<br/>  "entryCode": "123456",<br/>  "presidentId": "user123",<br/>  "createdAt": "2024-01-01T12:00:00",<br/>  "status": "pending",<br/>  "players": [...],<br/>  "currentPlayerIndex": 0,<br/>  "isClockWise": true,<br/>  "firstCoinState": null,<br/>  "secondCoinState": null,<br/>  "currentPlayer": null,<br/>  "isMyTurn": false,<br/>  "isPresident": false,<br/>  "gameState": "WAITING_ROOM",<br/>  "gameEndTime": null<br/>}``` |
| `/topic/session/{sessionId}` | 세션별 업데이트 | 모든 게임 액션의 결과가 이 topic으로 전송 |

## 2. 에러 메시지 (GameErrorDto)

| Topic | 설명 | JSON 구조 |
|-------|------|-----------|
| `/topic/session/{sessionId}/errors` | 세션별 에러 메시지 | ```json<br/>{<br/>  "errorType": "ERROR_SESSION_NOT_FOUND"<br/>}``` |

## 3. 에러 타입 분류표

| 카테고리 | 에러 코드 | 설명 |
|----------|-----------|------|
| **세션 관련** | `SESSION_NOT_FOUND` | 세션을 찾을 수 없음 |
| | `SESSION_CREATION_FAILED` | 세션 생성 실패 |
| **플레이어 관련** | `PLAYER_NOT_FOUND` | 플레이어를 찾을 수 없음 |
| | `PLAYER_ALREADY_JOINED` | 이미 참여한 플레이어 |
| | `PLAYER_DISCONNECTED` | 플레이어 연결 끊김 |
| **권한 관련** | `NOT_PRESIDENT` | 방장이 아님 |
| | `NOT_CURRENT_TURN` | 현재 턴이 아님 |
| **게임 상태 관련** | `GAME_IN_PROGRESS` | 게임이 진행 중 |
| | `GAME_NOT_STARTED` | 게임이 시작되지 않음 |
| | `WRONG_GAME_STATE` | 잘못된 게임 상태 |
| | `NOT_LEE_SOON_SIN_STATE` | 이순신 상태가 아님 |
| **게임 진행 관련** | `INSUFFICIENT_PLAYERS` | 플레이어 부족 |
| | `PRESIDENT_LEFT` | 방장이 나감 |
| | `ORDER_NOT_REGISTERED` | 순서가 등록되지 않음 |
| | `ALREADY_ORDERED` | 이미 순서 등록됨 |
| | `COIN_NOT_SET` | 동전이 설정되지 않음 |
| | `GAME_TIME_EXPIRED` | 게임 시간 만료 (10분) |
| **턴 스킵 관련** | `TURN_SKIPPED` | 플레이어 턴 스킵됨 (자동 턴 스킵만) |
| | `PLAYER_TIMEOUT` | 플레이어 응답 시간 초과 |
| **동전 관련** | `INVALID_COIN_TYPE` | 잘못된 동전 타입 |
| | `INVALID_COIN_STATE` | 잘못된 동전 상태 |
| **입장 코드 관련** | `INVALID_ENTRY_CODE` | 잘못된 입장 코드 |
| **네트워크 관련** | `NETWORK_TIMEOUT` | 네트워크 타임아웃 |
| | `CONNECTION_LOST` | 연결 끊김 |
| **시스템 관련** | `INTERNAL_SERVER_ERROR` | 내부 서버 에러 |

## 4. 게임 상태 값

| 상태 코드 | 설명 |
|-----------|------|
| `WAITING_ROOM` | 대기실 |
| `ORDER_REGISTER` | 순서 등록 |
| `GAME_PLAYING` | 게임 진행 중 |
| `LEE_SOON_SIN` | 이순신 화면 |
| `GAME_FINISHED` | 게임 종료 |

## 5. 턴 스킵 종류

| 종류 | 조건 | 동작 | 에러 메시지 |
|------|------|------|-------------|
| **수동 턴 스킵** | `skipTurn` 메서드 호출 | 현재 턴 플레이어가 명시적으로 스킵 요청 | ❌ 없음 |
| **자동 턴 스킵** | 플레이어 웹소켓 연결 끊김 | 현재 턴 플레이어의 연결이 끊어진 경우 자동 스킵 | ✅ `ERROR_TURN_SKIPPED` |

## 6. 메시지 전송 순서

### **일반 게임 액션**
| 순서 | Topic | 메시지 타입 | 설명 |
|------|-------|-------------|------|
| 1 | `/topic/session/{sessionId}` | GameStateDto | 업데이트된 게임 상태 |

### **자동 턴 스킵 (플레이어 연결 끊김)**
| 순서 | Topic | 메시지 타입 | 설명 |
|------|-------|-------------|------|
| 1 | `/topic/session/{sessionId}/errors` | GameErrorDto | 턴 스킵 에러 알림 |
| 2 | `/topic/session/{sessionId}/errors` | GameErrorDto | 플레이어 연결 끊김 에러 알림 |
| 3 | `/topic/session/{sessionId}` | GameStateDto | 업데이트된 게임 상태 |

### **게임 시간 만료 (자동 이순신 상태 전환)**
| 순서 | Topic | 메시지 타입 | 설명 |
|------|-------|-------------|------|
| 1 | `/topic/session/{sessionId}/errors` | GameErrorDto | 게임 시간 만료 에러 알림 |
| 2 | `/topic/session/{sessionId}` | GameStateDto | 이순신 상태로 전환된 게임 상태 |

## 7. 클라이언트 구독 가이드

### **필수 구독 Topic**
```dart
// 정상 응답 구독
subscribe('/topic/session/$sessionId');

// 에러 메시지 구독
subscribe('/topic/session/$sessionId/errors');
```

### **메시지 처리 예시**
```dart
// 에러 스트림 처리
subscribe('/topic/session/$sessionId/errors').listen((error) {
  switch (error.errorType) {
    case 'TURN_SKIPPED':
      showTurnSkippedMessage();
      break;
    case 'PLAYER_DISCONNECTED':
      showPlayerDisconnectedMessage();
      break;
    case 'GAME_TIME_EXPIRED':
      showGameTimeExpiredMessage();
      break;
  }
});

// 정상 스트림 처리
subscribe('/topic/session/$sessionId').listen((gameState) {
  updateGameState(gameState);
});
```

## 8. 메시지 흐름

| 단계 | 설명 |
|------|------|
| 1 | 클라이언트 요청 → `/app/{endpoint}` |
| 2 | 서버 처리 → 비즈니스 로직 실행 |
| 3 | 서버 응답 → 정상 응답은 `/topic/session/{sessionId}`, 에러는 `/topic/session/{sessionId}/errors` |
| 4 | 모든 클라이언트 → 각각의 topic을 구독하여 응답 수신 |

## 9. GameStateDto 필드 설명

| 필드명 | 타입 | 설명 |
|--------|------|------|
| `sessionId` | String | 세션 고유 ID |
| `entryCode` | String | 입장 코드 (6자리) |
| `presidentId` | String | 방장 사용자 ID |
| `createdAt` | LocalDateTime | 세션 생성 시간 |
| `status` | SessionStatus | 세션 상태 (pending, ordering, onGoing, finished) |
| `players` | List<PlayerDto> | 플레이어 목록 (게임 상태에 따라 다름) |
| `currentPlayerIndex` | int | 현재 턴 플레이어 인덱스 |
| `isClockWise` | boolean | 시계방향 여부 |
| `firstCoinState` | CoinState | 첫 번째 동전 상태 (head/tail/null) |
| `secondCoinState` | CoinState | 두 번째 동전 상태 (head/tail/null) |
| `currentPlayer` | PlayerDto | 현재 턴 플레이어 정보 |
| `isMyTurn` | boolean | 요청한 사용자의 턴 여부 |
| `isPresident` | boolean | 요청한 사용자가 방장 여부 |
| `gameState` | String | 현재 게임 상태 |
| `gameEndTime` | LocalDateTime | 게임 마감 시간 |

## 10. PlayerDto 필드 설명

| 필드명 | 타입 | 설명 |
|--------|------|------|
| `userId` | String | 사용자 고유 ID |
| `name` | String | 플레이어 이름 |
| `profileImageUrl` | String? | 프로필 이미지 URL (선택사항) |

## 11. CoinState 열거형

| 값 | 설명 |
|----|------|
| `head` | 동전 앞면 |
| `tail` | 동전 뒷면 |
| `null` | 동전 미설정 |

## 12. 변경 이력

| 버전 | 날짜 | 변경 사항 |
|------|------|-----------|
| 1.0 | 2024-01 | 초기 명세 작성 |
| 2.0 | 2024-01 | 메시지 타입 분리 (정상/에러), 자동 턴 스킵 추가, 에러 메시지 topic 분리 |
| 2.1 | 2024-01 | 게임 마감 시간 기능 추가 (10분 타이머, 자동 이순신 상태 전환) |
| 2.2 | 2024-01 | 정확한 타이머 기반 게임 마감 시간 체크 (스케줄러 대체) |
| 2.3 | 2024-01 | GameStateDto에 게임 마감 시간 정보 추가 (gameEndTime) | 