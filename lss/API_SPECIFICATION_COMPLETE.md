# LSS 게임 서버 완전 WebSocket API 명세서

## 📋 개요
- **서버 포트**: 8080
- **WebSocket 엔드포인트**: `/ws`
- **구독 경로**: 
  - `/topic/session-created` (세션 생성 응답)
  - `/topic/session-joined` (세션 입장 응답)
  - `/topic/session/{sessionId}` (게임 진행 응답)

---

## 🔌 WebSocket API

### 연결 설정
```
WebSocket: ws://localhost:8080/ws
STOMP 구독: 
- /topic/session-created (세션 생성 시)
- /topic/session-joined (세션 입장 시)
- /topic/session/{sessionId} (게임 진행 시)
```

---

## 📤 클라이언트 → 서버 요청

### 1. 세션 생성
**전송 경로:** `/app/create-session`

**요청 JSON:**
```json
{
  "userId": "user123",
  "name": "홍길동"
}
```

**응답 Topic:** `/topic/session-created`
**응답:** GameStateDto

---

### 2. 세션 입장
**전송 경로:** `/app/join-session`

**요청 JSON:**
```json
{
  "entryCode": "123456",
  "userId": "user456",
  "name": "이순신"
}
```

**응답 Topic:** `/topic/session-joined`
**응답:** GameStateDto

---

### 3. 동전 상태 설정
**전송 경로:** `/app/session/{sessionId}/coin`

**요청 JSON:**
```json
{
  "message": "",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "coinType": "first",
  "state": "head",
  "userId": "user123"
}
```

**응답 Topic:** `/topic/session/{sessionId}`
**응답:** GameStateDto

---

### 4. 다음 턴
**전송 경로:** `/app/session/{sessionId}/next-turn`

**요청 JSON:**
```json
{
  "message": "",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user123"
}
```

**응답 Topic:** `/topic/session/{sessionId}`
**응답:** GameStateDto

---

### 5. 순서 등록
**전송 경로:** `/app/session/{sessionId}/register-order`

**요청 JSON:**
```json
{
  "userId": "user123",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**응답 Topic:** `/topic/session/{sessionId}`
**응답:** GameStateDto

---

### 6. 게임 시작
**전송 경로:** `/app/session/{sessionId}/start-game`

**요청 JSON:**
```json
{
  "message": "",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user123"
}
```

**응답 Topic:** `/topic/session/{sessionId}`
**응답:** GameStateDto

---

### 7. 게임 진행 시작
**전송 경로:** `/app/session/{sessionId}/start-playing`

**요청 JSON:**
```json
{
  "message": "",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user123"
}
```

**응답 Topic:** `/topic/session/{sessionId}`
**응답:** GameStateDto

---

### 8. 이순신 화면에서 계속하기
**전송 경로:** `/app/session/{sessionId}/continue-lee-soon-sin`

**요청 JSON:**
```json
{
  "message": "",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user123"
}
```

**응답 Topic:** `/topic/session/{sessionId}`
**응답:** GameStateDto

---

### 9. 게임 상태 조회
**전송 경로:** `/app/session/{sessionId}/get-state`

**요청 JSON:**
```json
{
  "userId": "user123",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**응답 Topic:** `/topic/session/{sessionId}`
**응답:** GameStateDto

---

### 10. 세션 삭제
**전송 경로:** `/app/session/{sessionId}/delete`

**요청 JSON:**
```json
{
  "userId": "user123",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**응답 Topic:** `/topic/session/{sessionId}`
**응답:**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "gameState": "GAME_FINISHED",
  "status": "finished"
}
```

---

## 📥 서버 → 클라이언트 응답

### GameStateDto 응답 예시
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "entryCode": "123456",
  "presidentId": "user123",
  "createdAt": "2024-01-01T12:00:00",
  "status": "GAME_PLAYING",
  "players": [
    {
      "userId": "user123",
      "name": "홍길동",
      "profileImageUrl": "https://example.com/profile1.jpg"
    },
    {
      "userId": "user456",
      "name": "이순신",
      "profileImageUrl": "https://example.com/profile2.jpg"
    }
  ],
  "currentPlayerIndex": 0,
  "isClockWise": true,
  "firstCoinState": "head",
  "secondCoinState": "tail",
  "currentPlayer": {
    "userId": "user123",
    "name": "홍길동"
  },
  "isMyTurn": true,
  "isPresident": false,
  "gameState": "GAME_PLAYING"
}
```

---

## ⚠️ 에러 상황별 응답

### 1. 플레이어 부족 (1명 이하)
**응답 Topic:** `/topic/session/{sessionId}`
**응답:**
```json
{
  "errorType": "INSUFFICIENT_PLAYERS"
}
```

### 2. 방장 이탈
**응답 Topic:** `/topic/session/{sessionId}`
**응답:**
```json
{
  "errorType": "PRESIDENT_LEFT"
}
```

### 3. 강제 종료
**응답 Topic:** `/topic/session/{sessionId}`
**응답:**
```json
{
  "errorType": "FORCE_TERMINATED"
}
```

### 4. 플레이어 연결 끊김
**응답 Topic:** `/topic/session/{sessionId}`
**응답:**
```json
{
  "errorType": "PLAYER_DISCONNECTED"
}
```

---

## 📊 게임 상태별 응답

| 게임 상태 | players 필드 의미 | 설명 |
|-----------|------------------|------|
| `WAITING_ROOM` | 대기실 플레이어 | 순서 무관, 입장한 모든 플레이어 |
| `ORDERING` | 순서 등록된 플레이어 | 게임 순서대로 정렬된 플레이어 |
| `GAME_PLAYING` | 순서 등록된 플레이어 | 현재 게임 진행 중인 플레이어 |
| `LEE_SOON_SIN` | 순서 등록된 플레이어 | 이순신 화면 표시 중 |
| `GAME_FINISHED` | 빈 배열 | 게임 종료 |

---

## 🎯 핵심 포인트

1. **완전 WebSocket 기반**: 모든 기능이 WebSocket으로 통일
2. **실시간 양방향 통신**: 즉시 상태 동기화
3. **Topic 기반 응답**: 상황에 맞는 적절한 topic으로 응답
4. **에러 처리**: 실시간 에러 메시지 전송
5. **자동 퇴장**: WebSocket 연결 끊김으로 자동 처리
6. **MUX 방식**: 게임 상태별로 적절한 플레이어 리스트 반환

---

## 📝 클라이언트 구현 예시

### JavaScript/TypeScript
```javascript
// WebSocket 연결
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
  // 세션 생성/입장 응답 구독
  stompClient.subscribe('/topic/session-created', function(response) {
    const data = JSON.parse(response.body);
    console.log('세션 생성됨:', data);
    // 세션 ID 저장 후 게임 topic 구독
    subscribeToGame(data.sessionId);
  });
  
  stompClient.subscribe('/topic/session-joined', function(response) {
    const data = JSON.parse(response.body);
    console.log('세션 입장됨:', data);
    // 세션 ID 저장 후 게임 topic 구독
    subscribeToGame(data.sessionId);
  });
});

// 게임 topic 구독
function subscribeToGame(sessionId) {
  stompClient.subscribe('/topic/session/' + sessionId, function(response) {
    const data = JSON.parse(response.body);
    
    // 에러 상황 처리
    if (data.errorType) {
      const errorMessage = getErrorMessage(data.errorType);
      showSnackbar(errorMessage);
      
      // 게임 종료 관련 에러인 경우 로비로 이동
      if (['INSUFFICIENT_PLAYERS', 'PRESIDENT_LEFT', 'FORCE_TERMINATED', 'PLAYER_DISCONNECTED'].includes(data.errorType)) {
        navigateToLobby();
      }
      return;
    }
    
    // 정상 게임 상태 업데이트
    updateGameState(data);
  });
}

// 세션 생성
function createSession(userId, name) {
  stompClient.send("/app/create-session", {}, JSON.stringify({
    userId: userId,
    name: name
  }));
}

// 세션 입장
function joinSession(entryCode, userId, name) {
  stompClient.send("/app/join-session", {}, JSON.stringify({
    entryCode: entryCode,
    userId: userId,
    name: name
  }));
}

// 동전 상태 설정
function setCoinState(sessionId, coinType, state, userId) {
  stompClient.send("/app/session/" + sessionId + "/coin", {}, JSON.stringify({
    message: "",
    sessionId: sessionId,
    coinType: coinType,
    state: state,
    userId: userId
  }));
}

// 에러 타입에 따른 메시지 반환
function getErrorMessage(errorType) {
  const errorMessages = {
    'INSUFFICIENT_PLAYERS': '플레이어가 부족하여 게임이 종료됩니다.',
    'PRESIDENT_LEFT': '방장이 나가서 게임이 종료됩니다.',
    'FORCE_TERMINATED': '게임 중 오류가 발생하여 강제 종료됩니다.',
    'PLAYER_DISCONNECTED': '특정 플레이어가 연결이 끊어졌습니다.',
    'SESSION_NOT_FOUND': '세션을 찾을 수 없습니다.',
    'PLAYER_ALREADY_JOINED': '이미 참여한 플레이어입니다.',
    'NOT_PRESIDENT': '방장만 가능한 작업입니다.',
    'NOT_CURRENT_TURN': '현재 턴이 아닙니다.',
    'GAME_IN_PROGRESS': '이미 진행중인 게임입니다.',
    'WRONG_GAME_STATE': '잘못된 게임 상태입니다.',
    'INVALID_COIN_TYPE': '잘못된 동전 타입입니다.',
    'INVALID_COIN_STATE': '잘못된 동전 상태입니다.',
    'INVALID_ENTRY_CODE': '잘못된 입장 코드입니다.'
  };
  
  return errorMessages[errorType] || '알 수 없는 오류가 발생했습니다.';
}
```

---

## 🔄 게임 플로우

1. **WebSocket 연결** → `ws://localhost:8080/ws`
2. **세션 생성** → `/app/create-session` → `/topic/session-created`
3. **세션 입장** → `/app/join-session` → `/topic/session-joined`
4. **게임 topic 구독** → `/topic/session/{sessionId}`
5. **게임 시작** → `/app/session/{sessionId}/start-game`
6. **순서 등록** → `/app/session/{sessionId}/register-order`
7. **게임 진행** → `/app/session/{sessionId}/start-playing`
8. **동전 설정** → `/app/session/{sessionId}/coin`
9. **다음 턴** → `/app/session/{sessionId}/next-turn`
10. **이순신 계속** → `/app/session/{sessionId}/continue-lee-soon-sin`
11. **게임 종료** → 자동 또는 `/app/session/{sessionId}/delete`

---

## 🚀 완전 WebSocket 기반 아키텍처

- **단일 통신 방식**: WebSocket만 사용
- **Topic 기반 응답**: 상황에 맞는 적절한 topic으로 응답
- **실시간 동기화**: 모든 클라이언트가 즉시 상태 업데이트
- **자동 퇴장**: WebSocket 연결 끊김으로 자동 처리
- **에러 처리**: 실시간 에러 메시지 전송
- **상태 관리**: 서버에서 모든 게임 상태 관리
- **확장성**: 새로운 게임 기능 추가 시 WebSocket만 확장 