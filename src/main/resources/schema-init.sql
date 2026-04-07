-- WikiSprint DB 스키마 초기화 스크립트 (Google OAuth 버전)
-- PostgreSQL에서 실행: psql -U postgres -f schema-init.sql

CREATE SCHEMA IF NOT EXISTS wikisprint;
SET search_path TO wikisprint;

-- 계정 테이블 (Google 계정 1:1)
CREATE TABLE IF NOT EXISTS accounts (
    account_id      VARCHAR(50)   NOT NULL PRIMARY KEY,
    google_id       VARCHAR(255)  NOT NULL UNIQUE,
    email           VARCHAR(255)  NOT NULL,
    nick            VARCHAR(50)   NOT NULL,
    profile_img_url VARCHAR(500),
    is_admin        BOOLEAN       NOT NULL DEFAULT FALSE,
    last_login      TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 기존 DB 마이그레이션용 (이미 테이블이 존재하는 경우)
ALTER TABLE IF EXISTS accounts ADD COLUMN IF NOT EXISTS is_admin BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS total_games    INTEGER NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS total_clears   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS total_abandons INTEGER NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS best_record    BIGINT  DEFAULT NULL;

-- 기존 클리어 기록에서 최고 기록 마이그레이션 (칼럼 추가 직후 한 번만 적용)
UPDATE accounts a SET best_record = sub.min_ms
FROM (
    SELECT account_id, MIN(elapsed_ms) AS min_ms
    FROM game_records
    WHERE status = 'cleared' AND elapsed_ms IS NOT NULL
    GROUP BY account_id
) sub
WHERE a.account_id = sub.account_id AND a.best_record IS NULL;

CREATE INDEX IF NOT EXISTS idx_accounts_google_id ON accounts(google_id);
CREATE INDEX IF NOT EXISTS idx_accounts_email ON accounts(email);

-- 제시어 테이블
CREATE TABLE IF NOT EXISTS target_words (
    word_id     SERIAL        PRIMARY KEY,
    word        VARCHAR(100)  NOT NULL,
    difficulty  SMALLINT      NOT NULL DEFAULT 1,  -- 1: 쉬움, 2: 보통, 3: 어려움
    lang        VARCHAR(5)    NOT NULL DEFAULT 'ko', -- 언어 코드 (ko, en, ja)
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(word, lang)
);

-- 초기 제시어 데이터
INSERT INTO target_words (word, difficulty, lang) VALUES
    ('미국', 1, 'ko'),
    ('바나나', 1, 'ko'),
    ('배트맨', 2, 'ko'),
    ('환풍기', 3, 'ko'),
    ('United States', 1, 'en'),
    ('Banana', 1, 'en'),
    ('Batman', 2, 'en'),
    ('Ventilation', 3, 'en'),
    ('アメリカ合衆国', 1, 'ja'),
    ('バナナ', 1, 'ja'),
    ('バットマン', 2, 'ja'),
    ('換気扇', 3, 'ja')
ON CONFLICT (word, lang) DO NOTHING;

-- 게임 전적 테이블 초기화 (DROP 후 재생성)
DROP TABLE IF EXISTS game_records;
CREATE TABLE game_records (
    record_id    VARCHAR(50)   NOT NULL PRIMARY KEY,             -- REC-{UUID}
    account_id   VARCHAR(50)   NOT NULL REFERENCES accounts(account_id),
    target_word  VARCHAR(100)  NOT NULL,                         -- 제시어
    start_doc    VARCHAR(300)  NOT NULL,                         -- 시작 문서 제목
    nav_path     TEXT          NOT NULL,                         -- JSON 배열 문자열 (방문 경로)
    elapsed_ms   BIGINT,                                         -- 경과 시간 (밀리초, 클리어 시에만 설정)
    status       VARCHAR(20)   NOT NULL DEFAULT 'in_progress',  -- in_progress | cleared | abandoned
    last_article VARCHAR(300),                                   -- 마지막 도달 문서 (in_progress 추적용)
    played_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_status CHECK (status IN ('in_progress', 'cleared', 'abandoned'))
);

CREATE INDEX idx_game_records_account ON game_records(account_id, played_at DESC);
