## v1.10.0 (2026-04-13)

### Added
- 공유용 전적 조회 API 추가 (`POST /record/share/{shareId}`)
  - shareId = `record_id`에서 `REC-` prefix 제거한 UUID 문자열
  - `cleared` 상태 전적만 응답, 그 외 404 반환
  - 응답: `nick`, `profileImgUrl`, `targetWord`, `startDoc`, `navPath`, `elapsedMs` (내부 정보 제외)
  - JWT 인증 불필요 — 비로그인 사용자도 접근 가능
- `GameRecordMapper` — `selectRecordByRecordId(recordId)` 메서드 추가
  - 기존 `selectRecordById`는 `accountId` 필수 → 공유 조회용 신규 메서드 분리
- `GameRecordService` — `getSharedRecord(shareId)` 메서드 추가
- `SecurityConfig` — `/record/share/**`, `/api/record/share/**` permitAll 추가
- 서버 버전 1.9.0 → **1.10.0**

========================================================================================================
========================================================================================================
========================================================================================================

## v1.9.0 (2026-04-12)

### Added
- `GlobalExceptionHandler` 신규 추가 (`@RestControllerAdvice` — 전역 예외 처리)
  - `UnauthorizedException` → 401 UNAUTHORIZED
  - `FileException` → 400 BAD_REQUEST
  - `IllegalArgumentException` → 400 BAD_REQUEST
  - `RuntimeException` → 500 (메시지 미노출, "서버 오류가 발생했습니다.")
  - `Exception` → 500 (메시지 미노출)
- `AccountMapper` — `addTotalAbandons(uuid, count)` 벌크 포기 횟수 증가 메서드 추가
- `AccountMapper.xml` — `addTotalAbandons` 쿼리 추가 (`total_abandons + #{count}`)

### Changed
- `AccountController.getAccount()` — 공개 API에서 민감 정보 제거
  - `email`, `is_admin` 필드 응답에서 제외 (민감 정보는 `/account/me`에서만 제공)
- `AuthService.googleLogin()` — `Map.of()` → `HashMap` 전환 (null nationality NullPointerException 수정)
- `AuthService.reissueToken()` — refresh token 재발급 시 DB 조회로 최신 권한 재구성
  - refresh token에 auth claim이 없으므로 DB에서 is_admin 재확인 후 ROLE_ADMIN 부여
- `JwtTokenProvider` — 미사용 `createAccessToken()`, `createRefreshToken()` 메서드 삭제
- `GameRecordService.cleanupStaleRecords()` — N회 개별 UPDATE → 1회 벌크 UPDATE 최적화
  - stale 처리 시 `incrementTotalAbandons` N번 호출 → `addTotalAbandons(count)` 1회 호출
- `WikipediaService` — 캐시 최대 크기 500건 제한 (`putWithSizeLimit`, 메모리 누수 방지)
  - HTML 캐시 / 요약 캐시 모두 적용, 초과 시 가장 오래된 엔트리 제거
- 서버 버전 1.8.0 → **1.9.0**

========================================================================================================
========================================================================================================
========================================================================================================

## v1.8.0 (2026-04-12)

### Added
- `accounts` 테이블에 `nationality VARCHAR(2) DEFAULT NULL` 칼럼 추가 (ISO 3166-1 alpha-2, null = 무국적)
  - `schema-init.sql` — `ALTER TABLE accounts ADD COLUMN IF NOT EXISTS nationality ...` 마이그레이션 추가
- `POST /account/nationality/update` 엔드포인트 신규
  - 요청: `{ nationality: "KR" | null }` / JWT 인증 필수
  - 빈 문자열은 null로 자동 정규화, 2자리 코드 검증
- `AccountService.updateNationality()` 메서드 추가
- `AccountMapper.updateNationality()` + XML `<update>` 쿼리 추가
- `RankingRecordVO` — `nationality` 필드 추가 (accounts JOIN)

### Changed
- `AccountController.getMyAccount()`, `getAccount()` 응답에 `nationality` 필드 포함
- `AccountMapper.xml` — 3개 SELECT 쿼리에 `nationality` 칼럼 추가
- `RankingMapper.xml` — resultMap + `selectTop100` / `selectByUser` 쿼리에 `a.nationality` 추가
- `RankingController.rankingRecordToMap()` — `nationality` 필드 직렬화 추가
- 서버 버전 1.7.0 → **1.8.0**

========================================================================================================
========================================================================================================
========================================================================================================

## v1.7.0 (2026-04-08)

### Added
- `ranking_records` 테이블 신규 추가 (period_type × period_bucket × difficulty × account_id UNIQUE)
  - `idx_ranking_bucket_sort` 인덱스 (period_type, period_bucket, difficulty, elapsed_ms ASC, created_at ASC)
- `RankingController` — `POST /ranking/list` 엔드포인트 (JWT 옵셔널 파싱, 비로그인 조회 가능)
  - Response: `{ top100, me, bucketDate(KST), serverNow }`
- `RankingService` — Top 100 유지 로직 구현
  - 클리어마다 6개 버킷(3기간 × 난이도+all) 처리
  - 기존 기록보다 좋을 때만 갱신, 100위 초과분 자동 정리
  - 게임 클리어 트랜잭션과 분리(`@Transactional REQUIRES_NEW`)
  - KST 기준 일(오늘)/주(이번 주 월요일)/월(이번 달 1일) 버킷 산출
- `RankingMapper` + `RankingMapper.xml` — selectTop100/selectByUser/selectCount/selectWorstElapsedInTop100/insertRecord/updateRecord/deleteWorstBeyond100 (accounts JOIN 포함)
- `RankingRecordVO` — 랭킹 기록 VO (nickname, profileImageUrl JOIN 포함)
- `GameRecordMapper.selectRecordById` 추가 (랭킹 처리용 단건 조회)
- `TargetWordMapper.selectDifficultyByWord` 추가 (단어로 난이도 코드 조회)

### Changed
- `SecurityConfig` — `/ranking/**`, `/api/ranking/**` permitAll 추가
- `GameRecordService.completeRecord` — 클리어 후 `rankingService.tryInsertRanking()` 호출
  - 난이도 코드 조회 + navPath JSON 파싱으로 경로 길이 산출
  - 랭킹 처리 실패 시 warn 로그만 남기고 클리어 결과에 영향 없음
- 서버 버전 1.6.0 → **1.7.0**

========================================================================================================
========================================================================================================
========================================================================================================

## v1.6.0 (2026-04-07)

### Added
- `accounts` 테이블 — `best_record BIGINT` 칼럼 추가 (클리어한 게임 중 최단 클리어 시간, ms 단위)
  - 칼럼 추가 시 기존 `game_records` 에서 `MIN(elapsed_ms)` 마이그레이션 UPDATE 자동 적용
- `AccountVO` — `bestRecord Long` 필드 추가
- `AccountMapper` — `updateBestRecord(uuid, elapsedMs)` 메서드 추가
- `AccountMapper.xml` — `updateBestRecord` 쿼리 추가 (SQL 레벨 원자적 비교: `best_record IS NULL OR best_record > #{elapsedMs}`)
- `TargetWordMapper` — `selectRandomWordByDifficulty(lang, difficulty)` 메서드 추가
- `TargetWordMapper.xml` — `selectRandomWordByDifficulty` 쿼리 추가 (언어 + 난이도 필터, `ORDER BY RANDOM() LIMIT 1`)
- `WikiController` — `GET /wiki/target/random`에 `difficulty` 옵셔널 쿼리 파라미터 추가
  - `difficulty` 1~3: 해당 난이도 필터링, 미전달: 기존 전체 풀 랜덤 (하위 호환)

### Changed
- `GameRecordService.completeRecord` — 클리어 시 `accountMapper.updateBestRecord()` 호출 추가 (포기 게임은 비교 대상 제외)
- `GameRecordController./record/list` — `bestTimeMs` 계산 방식 변경: 최근 5건 스트림 MIN → `accounts.best_record` 직접 참조

========================================================================================================
========================================================================================================
========================================================================================================

## v1.5.0 (2026-04-06)

### Added
- `WikipediaService` — `ConcurrentHashMap` 기반 인메모리 문서 캐싱 추가 (TTL 1시간, `@Scheduled` 만료 엔트리 일괄 정리)
  - `getArticleHtml()` / `getArticleSummary()` 캐시 키: `lang:title`
  - `getRandomSummary()`는 캐싱 제외 (랜덤 특성상 무의미)
- `WikipediaService` — 지수 백오프 Retry 로직 추가 (`executeWithRetry`)
  - 429 Rate Limit 또는 네트워크/타임아웃 오류 시 최대 3회 재시도: 1초 → 2초 → 4초
  - 외부 라이브러리 미사용 (직접 구현)

### Notes
- Wikimedia API Usage Guidelines 준수: User-Agent 헤더 기존 설정 유지 (`WikiSprint/1.0 (https://github.com/wikisprint; contact@wikisprint.com) RestTemplate`)

========================================================================================================
========================================================================================================
========================================================================================================

## v1.4.0 (2026-04-06)

### Added
- `GameRecordController` — 전적 라이프사이클 엔드포인트 5개 (`/record/start`, `/record/update-path`, `/record/complete`, `/record/abandon`, `/record/list`)
- `GameRecordService` — 전적 생성 시 기존 `in_progress` 자동 포기, FIFO(최대 5건), stale 정리(60분), 누적 통계 증가 처리
- `GameRecordMapper` (Java + XML) — 전적 CRUD 7개 메서드 (`insertRecord`, `updateNavPath`, `completeRecord`, `abandonRecord`, `selectInProgressRecord`, `selectRecentRecords`, `deleteOldestRecords`, `abandonStaleRecords`)
- `GameRecordVO` — `status` (`in_progress`/`cleared`/`abandoned`), `lastArticle`, nullable `elapsedMs` 필드
- `game_records` 테이블 — `status` 3-state, `last_article`, `elapsed_ms` nullable, `CHECK` 제약조건 포함 신규 생성
- `accounts` 테이블에 누적 통계 컬럼 추가 — `total_games`, `total_clears`, `total_abandons` INTEGER DEFAULT 0
- `AccountMapper` / `AccountMapper.xml` — `incrementTotalGames`, `incrementTotalClears`, `incrementTotalAbandons` 메서드 추가, 전체 SELECT에 통계 컬럼 포함
- `AccountVO` — `totalGames`, `totalClears`, `totalAbandons` 필드 추가

### Changed
- `/record/list` 응답 summary — accounts 누적 통계 기반(`totalPlays`, `clearCount`, `giveUpCount`), bestTimeMs는 최근 5건 cleared에서 계산
- `/record/list` 호출 시 stale `in_progress` 자동 abandoned 전환 (`cleanupStaleRecords`)

========================================================================================================
========================================================================================================
========================================================================================================

## v1.3.0 (2026-04-02)

### Added
- `AdminController` 신규 생성 — 제시어 CRUD 3개 엔드포인트 (JWT + DB `is_admin` 이중 검증)
  - `POST /admin/words/list` — 전체 제시어 목록 조회
  - `POST /admin/words/add` — 제시어 추가 (body: `{ word, difficulty, lang }`)
  - `POST /admin/words/delete` — 제시어 삭제 (body: `{ wordId }`)
- `accounts` 테이블에 `is_admin BOOLEAN NOT NULL DEFAULT FALSE` 컬럼 추가 (schema-init.sql + ALTER TABLE 마이그레이션)
- `AccountVO`에 `isAdmin` 필드 추가 (`@JsonProperty("is_admin")`)

### Changed
- `AuthService.googleLogin()` — 관리자 계정에 `ROLE_ADMIN` authority 추가, 반환 Map에 `is_admin` 포함
- `AccountController` (`/account/me`, `/account/detail`) — 응답에 `is_admin` 필드 추가
- `AuthController.googleLogin()` — 응답 data Map에 `is_admin` 포함 (누락 버그 수정)
- `AccountMapper.xml` — 전체 SELECT 쿼리에 `is_admin` 컬럼 포함
- `SecurityConfig` — `/admin/**` 경로의 `hasRole("ADMIN")` 제거 → `anyRequest().authenticated()` + `AdminController.resolveAdmin()` DB 레벨 검증으로 전환

========================================================================================================
========================================================================================================
========================================================================================================

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
