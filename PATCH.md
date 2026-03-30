## v1.2.0 (2026-03-29)

### Changed
- `target_words` 테이블에 `lang` 칼럼 추가 — 언어별(ko/en/ja) 제시어 구분
  - `UNIQUE(word)` → `UNIQUE(word, lang)` 복합 유니크 제약조건 변경
  - 영어(en) 4건, 일본어(ja) 4건 시드 데이터 추가
- `WikipediaService` — 하드코딩 `ko.wikipedia.org` → `{lang}.wikipedia.org` 동적 URL 분기
  - 허용 언어 검증 (`ko`, `en`, `ja`), 비허용 시 `ko` 기본값
- `WikiController` — 4개 엔드포인트에 `?lang=` 쿼리 파라미터 추가 (기본값: `ko`)
  - `GET /api/wiki/random?lang=`
  - `GET /api/wiki/page/html/{title}?lang=`
  - `GET /api/wiki/page/summary/{title}?lang=`
  - `GET /api/wiki/target/random?lang=`
- `TargetWordMapper` — `selectRandomWord`에 `lang` 필터 추가, `insertWord`에 `lang` 칼럼 추가
- `TargetWordVO` — `lang` 필드 추가

========================================================================================================
========================================================================================================
========================================================================================================

## v1.1.0 (2026-03-26)

### Added
- `WikipediaService` — 한국어 Wikipedia REST API 프록시 (랜덤 문서 요약, 문서 HTML, 문서 요약)
- `WikiController` (`/api/wiki/**`) — Wikipedia API 4개 엔드포인트
  - `GET /api/wiki/random` — 랜덤 문서 요약
  - `GET /api/wiki/page/html/{title}` — 문서 HTML 반환
  - `GET /api/wiki/page/summary/{title}` — 문서 요약 반환
  - `GET /api/wiki/target/random` — DB에서 랜덤 제시어 조회
- `target_words` 테이블 — 제시어 DB 관리 (word_id, word, difficulty, created_at)
- `TargetWordVO`, `TargetWordMapper` (MyBatis) — 제시어 CRUD
- `mapper/game/TargetWordMapper.xml` — 랜덤 조회·전체 목록·추가·삭제 쿼리
- `schema-init.sql` — target_words DDL + 초기 제시어 4건 (미국/바나나/배트맨/환풍기, 난이도 포함)

### Changed
- `SecurityConfig` — `/wiki/**`, `/api/wiki/**` 경로 `permitAll` 추가 (비로그인 게임 플레이 지원)

========================================================================================================
========================================================================================================
========================================================================================================

## v1.0.0 (2026-03-24)

### Added

- WikiSprint 서버 초기화
- Google OAuth 인증 (GoogleIdTokenVerifier, ID Token 검증)
- 자동 가입: Google 계정 최초 로그인 시 자동 계정 생성
- JWT 토큰 발급 및 갱신 (access token + refresh token)
- 계정 관리 API (계정 조회, 닉네임 변경, 프로필 이미지 업로드/삭제)
- Spring Boot 3.5.7 + MyBatis + PostgreSQL
- 패키지: com.wikisprint.server
- 포트: 8585
- DB 스키마: wikisprint (accounts 단일 테이블)
