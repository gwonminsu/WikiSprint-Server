-- WikiSprint DB 스키마 초기화 스크립트 (Google OAuth 버전)
-- PostgreSQL에서 실행: psql -U postgres -f schema-init.sql

CREATE SCHEMA IF NOT EXISTS wikisprint;
SET search_path TO wikisprint;

-- 계정 테이블 (Google 계정 1:1)
CREATE TABLE IF NOT EXISTS accounts (
    account_id             VARCHAR(50)   NOT NULL PRIMARY KEY,
    google_id              VARCHAR(255)  NOT NULL UNIQUE,
    email                  VARCHAR(255)  NOT NULL,
    nationality            VARCHAR(2)    DEFAULT NULL,
    nick                   VARCHAR(50)   NOT NULL,
    profile_img_url        VARCHAR(500),
    is_admin               BOOLEAN       NOT NULL DEFAULT FALSE,
    last_login             TIMESTAMP,
    total_games            INTEGER       NOT NULL DEFAULT 0,
    total_clears           INTEGER       NOT NULL DEFAULT 0,
    total_abandons         INTEGER       NOT NULL DEFAULT 0,
    best_record            BIGINT        DEFAULT NULL,
    created_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deletion_requested_at  TIMESTAMP     DEFAULT NULL,
    CONSTRAINT uq_accounts_nick UNIQUE (nick)
);

-- 제시어 테이블
CREATE TABLE IF NOT EXISTS target_words (
    word_id      SERIAL        PRIMARY KEY,
    word         VARCHAR(100)  NOT NULL,
    difficulty   SMALLINT      NOT NULL DEFAULT 1,      -- 1: 쉬움, 2: 보통, 3: 어려움
    lang         VARCHAR(5)    NOT NULL DEFAULT 'ko',   -- 언어 코드 (ko, en, ja)
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (word, lang)
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

-- Ko-fi 후원 이력 테이블
DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS donations;
CREATE TABLE donations (
    donation_id            VARCHAR(50)   PRIMARY KEY,
    source                 VARCHAR(20)   NOT NULL DEFAULT 'kofi',
    kofi_account_id        VARCHAR(100),
    wikisprint_account_id  VARCHAR(50)   REFERENCES accounts(account_id) ON DELETE SET NULL,
    kofi_message_id        VARCHAR(100)  NOT NULL UNIQUE,
    type                   VARCHAR(30)   NOT NULL,
    supporter_name         VARCHAR(100),
    message                TEXT,
    is_account_linked_display BOOLEAN   NOT NULL DEFAULT FALSE,
    amount_cents           BIGINT        NOT NULL CHECK (amount_cents >= 0),
    currency               VARCHAR(10)   NOT NULL,
    is_anonymous           BOOLEAN       NOT NULL DEFAULT FALSE,
    received_at            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    created_at             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_donations_received_at
    ON donations (received_at DESC);

CREATE INDEX IF NOT EXISTS idx_donations_source_received
    ON donations (source, received_at DESC);

CREATE INDEX IF NOT EXISTS idx_donations_wikisprint_account
    ON donations (wikisprint_account_id);

-- 사용자와 후원 신고 내역
CREATE TABLE IF NOT EXISTS reports (
    report_id           VARCHAR(50)   PRIMARY KEY,
    reporter_account_id VARCHAR(50)   REFERENCES accounts(account_id) ON DELETE SET NULL,
    target_type         VARCHAR(20)   NOT NULL,
    target_account_id   VARCHAR(50)   REFERENCES accounts(account_id) ON DELETE SET NULL,
    target_donation_id  VARCHAR(50)   REFERENCES donations(donation_id) ON DELETE SET NULL,
    reason              VARCHAR(30)   NOT NULL,
    detail              VARCHAR(100),
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    resolved_by         VARCHAR(50)   REFERENCES accounts(account_id) ON DELETE SET NULL,
    resolved_at         TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reports_target_type CHECK (target_type IN ('ACCOUNT', 'DONATION')),
    CONSTRAINT chk_reports_reason CHECK (reason IN ('PROFILE_IMAGE', 'NICKNAME', 'DONATION_CONTENT', 'OTHER')),
    CONSTRAINT chk_reports_status CHECK (status IN ('PENDING', 'RESOLVED'))
);

CREATE INDEX IF NOT EXISTS idx_reports_status
    ON reports (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reports_target_account
    ON reports (target_account_id, status);

CREATE INDEX IF NOT EXISTS idx_reports_target_donation
    ON reports (target_donation_id, status);

-- 게임 기록 테이블 초기화 (DROP 후 재생성)
DROP TABLE IF EXISTS game_records;
CREATE TABLE game_records (
    record_id      VARCHAR(50)   NOT NULL PRIMARY KEY,             -- REC-{UUID}
    account_id     VARCHAR(50)   NOT NULL REFERENCES accounts(account_id),
    target_word    VARCHAR(100)  NOT NULL,                         -- 제시어
    start_doc      VARCHAR(300)  NOT NULL,                         -- 시작 문서 제목
    nav_path       TEXT          NOT NULL,                         -- JSON 배열 문자열 (방문 경로)
    elapsed_ms     BIGINT,                                         -- 경과 시간 (밀리초, 클리어 시에만 확정)
    status         VARCHAR(20)   NOT NULL DEFAULT 'in_progress',   -- in_progress | cleared | abandoned
    last_article   VARCHAR(300),                                   -- 마지막 도달 문서 (in_progress 추적용)
    played_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_status CHECK (status IN ('in_progress', 'cleared', 'abandoned'))
);

CREATE INDEX idx_game_records_account
    ON game_records (account_id, played_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_game_records_in_progress_per_account
    ON game_records (account_id)
    WHERE status = 'in_progress';

-- 공유 기록 스냅샷 테이블
CREATE TABLE IF NOT EXISTS shared_game_records (
    share_id         VARCHAR(50)   NOT NULL PRIMARY KEY,           -- 공유용 UUID 문자열
    account_id       VARCHAR(50)   NOT NULL REFERENCES accounts(account_id),
    record_id        VARCHAR(50)   NOT NULL UNIQUE,                -- 원본 game_records.record_id
    nick             VARCHAR(50)   NOT NULL,
    profile_img_url  VARCHAR(500),
    target_word      VARCHAR(100)  NOT NULL,
    start_doc        VARCHAR(300)  NOT NULL,
    nav_path         TEXT          NOT NULL,
    elapsed_ms       BIGINT        NOT NULL,
    expires_at       TIMESTAMP     NOT NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_shared_game_records_expires_at
    ON shared_game_records (expires_at ASC);

-- 랭킹 테이블 (Top 100 유지 구조)
CREATE TABLE IF NOT EXISTS ranking_records (
    id             SERIAL       PRIMARY KEY,
    account_id     VARCHAR(50)  NOT NULL REFERENCES accounts(account_id),
    period_type    VARCHAR(10)  NOT NULL,   -- daily | weekly | monthly
    period_bucket  DATE         NOT NULL,   -- 서버 KST 기준 버킷 시작일
    difficulty     VARCHAR(10)  NOT NULL,   -- all | easy | normal | hard
    elapsed_ms     BIGINT       NOT NULL,
    target_word    VARCHAR(100) NOT NULL,
    start_doc      VARCHAR(300) NOT NULL,
    path_length    INTEGER      NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ranking_period CHECK (period_type IN ('daily', 'weekly', 'monthly')),
    CONSTRAINT chk_ranking_diff CHECK (difficulty IN ('all', 'easy', 'normal', 'hard')),
    CONSTRAINT uq_ranking_bucket_user UNIQUE (period_type, period_bucket, difficulty, account_id)
);

CREATE INDEX IF NOT EXISTS idx_ranking_bucket_sort
    ON ranking_records (period_type, period_bucket, difficulty, elapsed_ms ASC, created_at ASC);

-- 동의 이력 테이블 (동의한 항목만 저장하므로 is_agreed 컬럼 없음)
CREATE TABLE IF NOT EXISTS consent_records (
    id               SERIAL       PRIMARY KEY,
    account_id       VARCHAR(50)  NOT NULL REFERENCES accounts(account_id),
    consent_type     VARCHAR(50)  NOT NULL,   -- terms_of_service, privacy_policy, age_verification, marketing_notification
    consent_version  VARCHAR(20)  NOT NULL,   -- v1.0 같은 약관 버전
    agreed_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (account_id, consent_type, consent_version)
);
