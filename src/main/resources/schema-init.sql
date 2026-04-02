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
