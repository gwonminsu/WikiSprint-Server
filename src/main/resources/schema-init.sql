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
    last_login      TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_accounts_google_id ON accounts(google_id);
CREATE INDEX IF NOT EXISTS idx_accounts_email ON accounts(email);

-- 제시어 테이블
CREATE TABLE IF NOT EXISTS target_words (
    word_id     SERIAL        PRIMARY KEY,
    word        VARCHAR(100)  NOT NULL UNIQUE,
    difficulty  SMALLINT      NOT NULL DEFAULT 1,  -- 1: 쉬움, 2: 보통, 3: 어려움
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 초기 제시어 데이터
INSERT INTO target_words (word, difficulty) VALUES
    ('미국', 1),
    ('바나나', 1),
    ('배트맨', 2),
    ('환풍기', 3)
ON CONFLICT (word) DO NOTHING;
