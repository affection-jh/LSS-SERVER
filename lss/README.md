# LSS Game Server

이순신 게임 서버 - Spring Boot 기반의 실시간 멀티플레이어 게임 서버

## 기술 스택

- **Spring Boot 3.5.3** (Java 17)
- **Spring WebSocket** (STOMP)
- **Spring Data JPA**
- **MySQL 8.0**
- **Gradle**

## 데이터베이스 설정

### 1. MySQL 설치 및 실행

```bash
# MySQL 8.0 설치 (Windows)
# https://dev.mysql.com/downloads/mysql/

# MySQL 서비스 시작
net start mysql80
```

### 2. 데이터베이스 초기화

#### 방법 1: 배치 파일 사용 (Windows)
```bash
# database 폴더로 이동
cd database

# 배치 파일 실행
init-db.bat
```

#### 방법 2: SQL 스크립트 직접 실행
```bash
# MySQL 접속
mysql -u root -p

# SQL 스크립트 실행
source database/init-db.sql;
```

#### 방법 3: 명령줄에서 직접 실행
```bash
# MySQL 명령어로 직접 실행
mysql -u root -p < database/init-db.sql
```

### 3. 테이블 구조 확인

```sql
USE lss_db;
SHOW TABLES;

-- 생성된 테이블들:
-- - players: 플레이어 정보
-- - sessions: 세션 정보
-- - session_players: 세션-플레이어 연결
-- - session_ordered_players: 순서 등록된 플레이어
-- - session_status_enum: 세션 상태 열거
-- - coin_state_enum: 동전 상태 열거
```

## 실행 방법

### 1. 의존성 설치
```bash
./gradlew build
```

### 2. 서버 실행
```bash
# 서버 실행
./gradlew bootRun
```

### 3. JAR 파일로 실행
```bash
# 빌드
./gradlew build

# 실행
java -jar build/libs/lss-0.0.1-SNAPSHOT.jar
```

## API 엔드포인트

### REST API
- `POST /api/sessions` - 세션 생성
- `POST /api/sessions/join` - 세션 입장
- `DELETE /api/sessions/{sessionId}/players/{userId}` - 세션 퇴장
- `GET /api/sessions/{sessionId}/state` - 게임 상태 조회

### WebSocket
- `/ws` - WebSocket 연결 엔드포인트
- `/app/session/{sessionId}/coin` - 동전 상태 설정
- `/app/session/{sessionId}/next-turn` - 다음 턴으로 이동
- `/topic/session/{sessionId}/user/{userId}` - 개별 사용자 게임 상태

## 성능 및 확장성

### 현재 설정 (MySQL)
- **세션**: 50,000-100,000개
- **동시 접속**: 5,000-10,000명
- **메모리 사용**: ~4-8GB

### 권장 하드웨어
- **CPU**: 4코어 이상
- **메모리**: 8GB 이상
- **디스크**: SSD 100GB 이상
 