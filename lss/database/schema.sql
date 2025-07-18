
-- 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS lss_db
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE lss_db;

-- 세션 테이블 (간단화)
CREATE TABLE IF NOT EXISTS sessions (
    id VARCHAR(255) PRIMARY KEY,
    entry_code VARCHAR(6) NOT NULL UNIQUE,
    president_id VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL, -- pending, ordering, onGoing, finished
    current_player_index INT NOT NULL DEFAULT 0,
    is_clock_wise BOOLEAN NOT NULL DEFAULT TRUE,
    first_coin_state VARCHAR(10), -- head, tail
    second_coin_state VARCHAR(10), -- head, tail
    pending_players_json JSON, -- 대기실 플레이어 (순서 무관)
    game_end_time DATETIME, -- 게임 마감 시간 (순서 등록 후 10분)
    is_lee_soon_sin_by_time_expired BOOLEAN -- 이순신 상태가 시간 초과로 인한 것인지 구분
);

-- 순서 등록된 플레이어 테이블 (순서 엄격히 관리)
CREATE TABLE IF NOT EXISTS session_ordered_players (
    session_id VARCHAR(255) NOT NULL,
    player_user_id VARCHAR(255) NOT NULL,
    player_order INT NOT NULL, -- 순서 인덱스
    name VARCHAR(100) NOT NULL,
    profile_image_url VARCHAR(500),
    PRIMARY KEY (session_id, player_order),
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);

-- 인덱스 생성
CREATE INDEX idx_sessions_entry_code ON sessions(entry_code);

-- 기존 테이블에 새로운 컬럼 추가 (이미 존재하는 경우 무시)
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS game_end_time DATETIME;
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS is_lee_soon_sin_by_time_expired BOOLEAN;
