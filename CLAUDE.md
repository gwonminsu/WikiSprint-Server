# CLAUDE.md

이 파일은 Claude Code가 WikiSprint-Server 저장소에서 작업할 때 참고하는 가이드입니다.

## 참고 문서

| 문서 | 설명 |
|------|------|
| [PATCH.md](./PATCH.md) | 버전별 변경 내역 (패치 노트) |
| [schema-init.sql](./src/main/resources/schema-init.sql) | DB 스키마 초기화 스크립트 |

## 프로젝트 개요

WikiSprint-Server는 WikiSprint 프로젝트의 Spring Boot 백엔드 서버입니다.

**기술 스택:** Java 17, Spring Boot 3.5.7, Spring Security, MyBatis 3.0.5, PostgreSQL, JWT (JJWT 0.11.5), google-api-client 2.7.0

## 주요 명령어

```bash
# 빌드
./gradlew build

# 서버 실행 (포트 8585)
./gradlew bootRun

# 테스트 실행
./gradlew test

# 클린 빌드
./gradlew clean build
```

## 아키텍처

**계층 구조:**
```
Controller → Service → Mapper (MyBatis) → PostgreSQL
                          ↓
                    VO (Value Objects)
```

**패키지 구조:**
```
com.wikisprint.server/
├── controller/          # AuthController, AccountController, WikiController, AdminController, GameRecordController, RankingController, DonationController, DonationAdminController, DonationWebhookController
├── service/             # AuthService, AccountService, WikipediaService, GameRecordService, RankingService, DonationService, AccountDeletionScheduler, NicknameGenerator
├── mapper/              # AccountMapper, TargetWordMapper, GameRecordMapper, RankingMapper, ConsentMapper, DonationMapper (MyBatis DAO)
├── vo/                  # AccountVO, TargetWordVO, GameRecordVO, RankingRecordVO, ConsentRecordVO, DonationVO
├── dto/                 # GoogleLoginReqDTO, RegisterReqDTO, ConsentItemDTO, ApiResponse<T>, AccountTransferDonationCreateRequestDTO, DonationResponseDTO, PendingAccountTransferDonationResponseDTO
├── event/               # DonationSavedEvent (현재 미사용 스캐폴드)
└── global/
    ├── config/          # SecurityConfig, GoogleOAuthConfig, RestTemplateConfig
    └── common/
        ├── auth/        # JwtTokenProvider, JwtAuthenticationFilter
        ├── status/      # 커스텀 예외
        ├── storage/     # FileStorageService (interface), LocalFileStorageService
        ├── ConsentConstants  # 약관 타입·버전·필수 목록 상수
        ├── GlobalExceptionHandler
        └── filter/          # SimpleRateLimitFilter (IP+URI 기준 레이트리밋)
```

**MyBatis Mapper XML 위치:**
- `src/main/resources/mapper/user/` — AccountMapper, ConsentMapper
- `src/main/resources/mapper/game/` — TargetWordMapper, GameRecordMapper, RankingMapper
- `src/main/resources/mapper/` — DonationMapper.xml

## 핵심 시스템

### 인증 플로우 (Google OAuth)

```
POST /auth/google (credential: Google ID Token)
  → GoogleIdTokenVerifier.verify()
  → payload에서 sub(google_id), email 추출
  → accountMapper.selectAccountByGoogleId()
  → 기존 계정 없으면: is_new_user=true + id_token_string 반환 (계정 미생성)
  → 탈퇴 대기 계정이면: is_deletion_pending=true + deletion_scheduled_at 반환 (JWT 미발급)
  → 정상 계정: JWT access token + refresh token 발급 및 반환

POST /auth/register (credential: Google ID Token, consents: 약관 동의 목록)
  → GoogleIdTokenVerifier.verify()
  → 필수 동의 항목(terms_of_service, privacy_policy, age_verification) 서버 검증
  → NicknameGenerator로 자동 닉네임 생성
  → accounts 삽입 + consent_records 삽입 (단일 트랜잭션)
  → JWT access token + refresh token 발급 및 반환

POST /auth/cancel-deletion (credential: Google ID Token)
  → 본인 확인 후 deletion_requested_at = NULL 복원
  → JWT access token + refresh token 발급 및 반환
```

### 데이터베이스 스키마 (PostgreSQL)

- 스키마: `wikisprint` (connection-init-sql로 설정)
- 테이블: `accounts` (계정, google_id로 식별)
  - `account_id` VARCHAR(50) PK
  - `google_id` VARCHAR(255) UNIQUE
  - `email` VARCHAR(255)
  - `nick` VARCHAR(50) UNIQUE
  - `profile_img_url` VARCHAR(500)
  - `nationality` VARCHAR(2) DEFAULT NULL (ISO 3166-1 alpha-2 국가 코드, null = 무국적)
  - `is_admin` BOOLEAN NOT NULL DEFAULT FALSE
  - `total_games`, `total_clears`, `total_abandons` INTEGER NOT NULL DEFAULT 0 (누적 통계)
  - `best_record` BIGINT DEFAULT NULL (전체 클리어 기록 중 최단 시간, ms. null = 클리어 기록 없음)
  - `deletion_requested_at` TIMESTAMP DEFAULT NULL (null = 정상 계정, non-null = 탈퇴 대기 상태)
  - `last_login`, `created_at`, `updated_at`
- 테이블: `target_words` (제시어)
  - `word_id` SERIAL PK
  - `word` VARCHAR(100)
  - `difficulty` SMALLINT (1: 쉬움, 2: 보통, 3: 어려움)
  - `lang` VARCHAR(5) (ko, en, ja)
  - `created_at` TIMESTAMP
  - UNIQUE(word, lang)
- 테이블: `game_records` (게임 전적)
  - `record_id` VARCHAR(50) PK
  - `account_id` VARCHAR(50) FK → accounts
  - `target_word` VARCHAR(100)
  - `start_doc` VARCHAR(300)
  - `nav_path` TEXT (JSON 배열 문자열)
  - `elapsed_ms` BIGINT (nullable — cleared 시에만 설정)
  - `status` VARCHAR(20) — `in_progress` | `cleared` | `abandoned`
  - `last_article` VARCHAR(300) (in_progress 추적용)
  - `played_at`, `created_at` TIMESTAMP
  - CHECK (status IN ('in_progress', 'cleared', 'abandoned'))
  - partial unique index: `uq_game_records_in_progress_per_account` — `status = 'in_progress'`일 때 계정당 1건만 허용
- 테이블: `shared_game_records` (공유 전적 스냅샷)
  - `share_id` UUID PK
  - `record_id` VARCHAR(50) FK → game_records
  - `account_id` VARCHAR(50) FK → accounts
  - `target_word`, `start_doc`, `nav_path`, `elapsed_ms`, `path_length`
  - `expires_at`, `created_at` TIMESTAMP
- 테이블: `consent_records` (약관 동의 이력)
  - `id` SERIAL PK
  - `account_id` VARCHAR(50) FK → accounts
  - `consent_type` VARCHAR(50) — `terms_of_service` | `privacy_policy` | `age_verification` | `marketing_notification`
  - `consent_version` VARCHAR(20) (예: `v1.0`)
  - `agreed_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  - UNIQUE(account_id, consent_type, consent_version) — 중복 동의 방지
  - row 존재 = 동의, row 부재 = 미동의 (is_agreed 컬럼 없음)
- 테이블: `ranking_records` (랭킹 기록)
  - `id` SERIAL PK
  - `account_id` VARCHAR(50) FK → accounts
  - `period_type` VARCHAR(10) — `daily` | `weekly` | `monthly`
  - `period_bucket` DATE — KST 기준 버킷 시작일 (일=오늘, 주=이번 주 월요일, 월=이번 달 1일)
  - `difficulty` VARCHAR(10) — `all` | `easy` | `normal` | `hard`
  - `elapsed_ms` BIGINT
  - `target_word` VARCHAR(100)
  - `start_doc` VARCHAR(300)
  - `path_length` INTEGER
  - `created_at` TIMESTAMP
  - UNIQUE(period_type, period_bucket, difficulty, account_id)
  - INDEX: idx_ranking_bucket_sort (period_type, period_bucket, difficulty, elapsed_ms ASC, created_at ASC)
- 테이블: `donations` (후원 기록)
  - `donation_id` VARCHAR(50) PK
  - `source` VARCHAR(20) DEFAULT 'kofi' — `kofi` | `account transfer`
  - `kofi_account_id` VARCHAR(100)
  - `wikisprint_account_id` VARCHAR(50) FK → accounts ON DELETE SET NULL
  - `kofi_message_id` VARCHAR(100) NOT NULL UNIQUE (계좌이체는 합성 `KOFI-<uuid>`)
  - `type` VARCHAR(30) — `Donation` | `PendingTransfer` 등
  - `supporter_name` VARCHAR(100)
  - `message` TEXT
  - `amount_cents` BIGINT CHECK (>= 0) (KRW 계좌이체: 커피잔 × 2000 × 100)
  - `currency` VARCHAR(10)
  - `is_anonymous` BOOLEAN DEFAULT FALSE
  - `received_at` TIMESTAMP (계좌이체 확인 시 설정)
  - `created_at` TIMESTAMP
  - INDEX 3종: `idx_donations_received_at`, `idx_donations_source_received`, `idx_donations_wikisprint_account`

### 보안 설정

- CORS 허용: `http://localhost:5969`, `http://13.209.255.179:5969`, `https://main.d11crzf9vrq2hy.amplifyapp.com`, `https://wiki-sprint.com`, `https://www.wiki-sprint.com`
- 공개 엔드포인트: `/auth/**`, `/error/**`, `/account/profile/image/**`, `/wiki/**`, `/ranking/**`, `/donations/**`, `/webhook/**`
- 보호된 엔드포인트: JWT Bearer 토큰 필요
- 관리자 엔드포인트 (`/admin/**`): JWT 인증 + `AdminController.resolveAdmin()` DB 레벨 `is_admin` 이중 검증

### 인증 API (`/api/auth/**`)

| 엔드포인트 | 설명 | 인증 |
|---|---|---|
| `POST /auth/google` | Google 로그인 (신규/탈퇴대기/정상 분기) | 공개 |
| `POST /auth/google/code` | iOS 전용 OAuth code flow 로그인 | 공개 |
| `POST /auth/register` | 약관 동의 후 신규 가입 (body: `{ credential, consents[] }`) | 공개 |
| `POST /auth/cancel-deletion` | 탈퇴 취소 + 로그인 (body: `{ credential }`) | 공개 |
| `POST /auth/reissue` | JWT 재발급 | Refresh Token |
| `POST /auth/logout` | 로그아웃 | JWT |

### 계정 API (`/api/account/**`)

| 엔드포인트 | 설명 | 인증 |
|---|---|---|
| `POST /account/me` | 내 계정 정보 조회 (email, is_admin 포함) | JWT |
| `POST /account/info/{accountId}` | 공개 계정 정보 조회 | 공개 |
| `POST /account/nick/update` | 닉네임 변경 | JWT |
| `POST /account/nationality/update` | 국적 변경 | JWT |
| `POST /account/profile/image/upload` | 프로필 이미지 업로드 | JWT |
| `POST /account/profile/image/remove` | 프로필 이미지 삭제 | JWT |
| `POST /account/delete/request` | 회원탈퇴 요청 (`?immediate=false`) | JWT |

### 관리자 API (`/api/admin/**`)

| 엔드포인트 | 설명 | 인증 |
|---|---|---|
| `POST /admin/words/list` | 전체 제시어 목록 조회 | JWT + is_admin |
| `POST /admin/words/add` | 제시어 추가 (body: `{ word, difficulty, lang }`) | JWT + is_admin |
| `POST /admin/words/delete` | 제시어 삭제 (body: `{ wordId }`) | JWT + is_admin |

### 후원 API (`/api/donations/**`)

| 엔드포인트 | 설명 | 인증 |
|---|---|---|
| `POST /donations/latest` | 최근 후원 Top 20 조회 (`type != 'PendingTransfer'`) | 공개 |
| `POST /donations` | 전체 후원 목록 조회 | 공개 |
| `POST /donations/{donationId}` | 단건 상세 조회 | 공개 |
| `POST /donations/account-transfer/request` | 국내 계좌이체 후원 요청 (body: `{ coffeeCount, nickname, remitterName, message, anonymous }`) | 공개 (JWT 옵셔널) |

### 관리자 후원 API (`/api/admin/donations/**`)

| 엔드포인트 | 설명 | 인증 |
|---|---|---|
| `POST /admin/donations/account-transfer/pending` | 미확정 계좌이체 대기 목록 조회 | JWT + is_admin |
| `POST /admin/donations/account-transfer/confirm` | 입금 확인 처리 (body: `{ donationId }`) — `type='Donation'` + `received_at=NOW()` | JWT + is_admin |

### 웹훅 API (`/api/webhook/**`)

| 엔드포인트 | 설명 | 인증 |
|---|---|---|
| `POST /webhook/kofi` | Ko-fi 후원 웹훅 수신 (`application/x-www-form-urlencoded`) — 토큰 상수-시간 비교, 중복 차단 | 공개 (토큰 검증) |

### 게임 전적 API (`/api/record/**`)

| 엔드포인트 | 설명 | 인증 |
|---|---|---|
| `POST /record/start` | 전적 생성 (in_progress), body: `{ targetWord, startDoc }` — 진행 중 게임이 있으면 409 | JWT |
| `POST /record/update-path` | 경로 갱신, body: `{ recordId, navPath, lastArticle }` | JWT |
| `POST /record/complete` | 클리어 처리, body: `{ recordId, navPath, elapsedMs }` | JWT |
| `POST /record/abandon` | 포기 처리, body: `{ recordId }` | JWT |
| `POST /record/list` | 전적 목록 + 누적 통계 조회 (stale 자동 정리 포함, `difficulty` 응답 포함) | JWT |
| `POST /record/share` | 공유 전용 스냅샷 생성 또는 재사용, body: `{ recordId }` | JWT |
| `POST /record/share/{shareId}` | 유효한 공유 스냅샷 조회 | 공개 |

**전적 라이프사이클:** `in_progress` → `cleared` or `abandoned`  
**동시성 제약:** fresh `in_progress` 전적이 있으면 `/record/start`는 `409 CONFLICT`로 거절  
**FIFO 정책:** 계정당 터미널 전적(cleared/abandoned) 최대 5건 유지  
**stale 정리:** `/record/list` 호출 시 60분 경과 `in_progress` 자동 `abandoned` 전환

> 최근 전적 응답의 `difficulty`는 `target_words` 참조 기반 조회 필드이며, `GameRecordVO`, `GameRecordController`, `GameRecordMapper.xml`을 함께 맞춰야 합니다.
> 공유 전적은 `shared_game_records` 스냅샷을 사용하며, 조회 시점에 `expires_at > NOW()` 조건으로 즉시 만료 차단됩니다.

## 외부 의존성

- **PostgreSQL:** `127.0.0.1:5432/postgres` (스키마: wikisprint)
- **Google OAuth:** `GOOGLE_CLIENT_ID` 환경변수로 설정

### 랭킹 API (`/api/ranking/**`)

| 엔드포인트 | 설명 | 인증 |
|---|---|---|
| `POST /ranking/list` | 기간×난이도 Top 100 조회 (내 기록 포함) | 공개 (JWT 옵셔널) |

**Request body:**
```json
{ "periodType": "daily|weekly|monthly", "difficulty": "all|easy|normal|hard" }
```

**Response data:**
```json
{ "top100": [...], "me": {...} | null, "bucketDate": "YYYY-MM-DD", "serverNow": "..." }
```

**버킷 정책 (KST 기준):**
- `daily` → 오늘 날짜
- `weekly` → 이번 주 월요일
- `monthly` → 이번 달 1일

**Top 100 유지 정책:**
- 계정당 버킷(period_type × period_bucket × difficulty)별 1건만 유지
- 더 좋은 기록(elapsed_ms 더 짧음)일 때만 갱신
- 신규 삽입 시 100건 초과 시 최하위 기록 삭제
- 게임 클리어마다 6개 버킷(3기간 × 난이도+all)에 자동 삽입

### Wikipedia API (`/api/wiki/**`)

| 엔드포인트 | 설명 | 인증 |
|---|---|---|
| `GET /wiki/random?lang=ko` | 랜덤 위키피디아 문서 요약 | 공개 |
| `GET /wiki/page/html/{title}?lang=ko` | 문서 HTML | 공개 |
| `GET /wiki/page/summary/{title}?lang=ko` | 문서 요약 | 공개 |
| `GET /wiki/target/random?lang=ko[&difficulty=1]` | 랜덤 제시어 (difficulty 1~3 옵셔널, 미전달=오마카세) | 공개 |

## API 규칙

- 모든 엔드포인트는 POST 메서드 사용 (GET 일부 예외: `/wiki/**`)
- 요청/응답 본문은 JSON 형식
- 인증: `Authorization: Bearer {access_token}` 헤더
- 응답 래퍼: `ApiResponse<T>` (`{ data, message, auth }`)

## 파일 저장 경로

- 저장 루트는 `app.storage.root` 설정으로 관리 (기본값 `./storage`).
- **운영 환경:** 환경변수 `APP_STORAGE_ROOT=/opt/wikisprint/storage` 주입으로 덮어씀. `application-prod.yaml` 신규 파일 불필요.
- 구현체: `LocalFileStorageService` (`global/common/storage/`). 부팅 시 `@PostConstruct`에서 루트 디렉토리를 자동 생성함.
- 향후 S3 전환 시 `FileStorageService` 인터페이스를 구현하는 `S3FileStorageService`를 추가하고 DI 교체만 하면 됨.
- **DB의 `profile_img_url`** 은 저장 루트 프리픽스 없는 상대 경로(`{acc}/{acc}/profile/{FIL-xxx}.ext`)이므로, 루트가 바뀌어도 DB 변경 불필요.
- 운영 서버 초기 준비:
  ```
  sudo mkdir -p /opt/wikisprint/storage
  sudo chown ubuntu:ubuntu /opt/wikisprint/storage
  ```
- 기존 `storage/` 데이터 마이그레이션: `rsync -av storage/ /opt/wikisprint/storage/`

---

## Claude Code 명령어

### patch note
버전을 생성하고 변경 내용을 PATCH.md 파일에 자동으로 작성합니다.

**실행 방법:** `patch note` 입력

**동작:**
1. PATCH.md에서 최신 버전 확인
2. git log/diff를 분석하여 변경 내용 파악
3. PATCH.md 최상단에 새 버전 항목 추가
4. 버전 사이 구분선: `=` 104개 × 3줄
5. 커밋 제안만 함 (커밋·푸시는 사용자가 직접 수행)

**커밋 규칙:**
- 제목: `feat: 대표 변경사항 (vX.X.X)`

---

## 최근 변경 메모 (v1.16.2)

- `RankingAlertResponseDTO`에 `periodType`, `difficulty` 필드가 추가되어 프론트가 랭킹 알림 버킷을 직접 구분할 수 있습니다.
- `RankingService.tryInsertRanking()`은 이제 단일 알림이 아니라 알림 목록을 반환하며, 플레이한 난이도의 `daily / weekly / monthly` 버킷에서 각각 알림을 만들 수 있습니다.
- 랭킹 알림 생성 기준은 기존 `daily + all` 단일 버킷에서 `easy / normal / hard × daily / weekly / monthly` 구조로 확장됐고, `all` 버킷은 집계만 유지합니다.
- `GameRecordService.completeRecord()`와 `GameRecordController`는 단수 `rankingAlert` 대신 복수 `rankingAlerts` 응답을 사용합니다.
- `RankingAlertService`의 최근 알림 큐 최대 보관 수는 500건으로 늘었고, `RankingServiceTest`가 추가되어 기간별 알림 생성 범위를 검증합니다.

## 최근 변경 메모 (v1.16.1)

- `DonationResponseDTO`에 `alertCreatedAt` 필드가 추가됐습니다.
- `DonationService.getRecentAlertDonations()`와 재생 알림 응답이 `event.createdAt()`을 함께 내려줍니다.
- 일반 후원 응답 DTO 생성도 `receivedAt`을 `alertCreatedAt` fallback으로 포함해 프론트가 알림 재생 기준 시각을 안정적으로 계산할 수 있습니다.

## 최근 변경 메모 (v1.16.0)

- `POST /api/ranking/alerts/recent` (공개) 엔드포인트와 `RankingAlertService`가 추가됐습니다. in-memory `ConcurrentLinkedDeque`로 최근 10분 / 최대 200건의 알림을 보관합니다.
- `RankingService.tryInsertRanking()`이 `daily`+`all` 버킷에서 클리어 전후 Top100을 비교해 `RankingAlertResponseDTO`(kind: new-entry/overtake)를 반환하도록 변경됐습니다.
- `GameRecordService.completeRecord()`가 알림을 publish하고 반환하며, `GameRecordController`가 클리어 응답 본문에 `CompleteRecordResponseDTO { rankingAlert }`을 실어 보냅니다.
- `RankingAlertResponseDTO`, `RankingAlertPlayerDTO`, `CompleteRecordResponseDTO` DTO가 추가됐습니다.

## 최근 변경 메모 (v1.15.3)

- `game_records`는 `status = 'in_progress'` 조건에서 계정당 1건만 허용합니다. `schema-init.sql`에 partial unique index가 추가됐습니다.
- `POST /record/start`는 fresh 진행 중 전적이 있으면 `ConflictException` 기반 `409 CONFLICT`를 반환합니다. stale 전적만 자동 포기 후 새 게임을 시작합니다.
- `GameRecordService.completeRecord()`와 `abandonRecord()`는 실제 상태 전이가 성공한 경우에만 통계, 최고 기록, 랭킹, FIFO 정리를 반영합니다.
- `GameRecordServiceTest`는 중복 완료/포기 no-op과 진행 중 게임 중복 시작 차단 케이스를 검증합니다.

## 최근 변경 메모 (v1.15.2)

- `GameRecordService`에 클리어 경로 검증이 추가됐습니다.
  - `validateCompletedPath()`가 완료 저장(`completeRecord`)과 공유 스냅샷 생성(`createOrGetShareRecord`) 양쪽에서 `navPath` 마지막 문서가 `target_word`와 정규화 기준으로 일치하는지 확인합니다.
  - `normalizeTitle()`은 `URLDecoder.decode` + `trim` + `toLowerCase(Locale.ROOT)` + 언더스코어→공백 치환을 적용해 프론트와 같은 비교 기준을 사용합니다.
- `GameRecordServiceTest`(Mockito 기반)가 추가됐습니다.
  - 경로 마지막 문서와 `target_word` 불일치 시 완료 저장과 공유 생성 모두 `IllegalArgumentException`으로 차단되는지, `gameRecordMapper.completeRecord` / `sharedGameRecordMapper.insertShareRecord` 같은 부작용이 발생하지 않는지 검증합니다.

## 최근 변경 메모 (v1.15.1)

- `AccountService`의 프로필 이미지 검열은 8px 다운샘플 후 원본 크기 업스케일 기반으로 정리됐습니다. 추가 박스 블러 컨볼루션은 제거됐습니다.
- `censored-logo.png`는 `@PostConstruct`에서 선로딩 후 캐시합니다. 검열 요청마다 리소스를 다시 읽지 않습니다.
- 이번 패치는 신고/후원/계정 API 계약 변경 없이 프로필 검열 결과물의 안정성과 성능을 보정하는 범위입니다.

## 최근 변경 메모 (v1.14.1)

- CORS 허용 도메인에 `https://wiki-sprint.com`, `https://www.wiki-sprint.com`이 추가됐습니다 (`SecurityConfig`).
- 익명 후원자(`is_anonymous=true`)의 메시지 마스킹이 제거됐습니다. 기존에는 `message`도 null로 처리했지만, 변경 후 `message`는 원본 그대로 반환되고 `supporterName`과 `accountProfileImgUrl`만 null 처리합니다 (`DonationService.toResponseDto`).

## 최근 변경 메모 (v1.14.0)

- 파일 저장 경로가 외부화됐습니다. `FileStorageUtil`이 삭제되고 `FileStorageService` 인터페이스 + `LocalFileStorageService` 구현체로 대체됐습니다 (`global/common/storage/`).
- 저장 루트는 `app.storage.root: ${APP_STORAGE_ROOT:./storage}` 설정으로 관리합니다.
- `AccountController`, `AccountService`는 `FileStorageUtil` 대신 `FileStorageService` 인터페이스에 의존합니다.

## 최근 변경 메모 (v1.13.0)

- 후원 기능이 추가됐습니다. `donations` 테이블 하나로 Ko-fi 웹훅(`source='kofi'`)과 국내 계좌이체(`source='account transfer'`)를 모두 수용합니다.
- 국내 계좌이체는 `type='PendingTransfer'`로 삽입되고, 관리자 확인 후 `type='Donation'`으로 전환됩니다. 공개 조회 API는 `type != 'PendingTransfer'` 필터로 미확정 요청을 차단합니다.
- Ko-fi 웹훅 토큰 검증은 `MessageDigest.isEqual`로 상수-시간 비교를 수행합니다(타이밍 어택 방지).
- `SimpleRateLimitFilter`가 IP+URI 기준 60초 롤링 윈도우 레이트리밋을 적용합니다(`/webhook/kofi` = 60/min, `/donations/**` = 30/min).
- `AccountService.deleteAccountImmediately`에 `donationMapper.clearWikiSprintAccountIdByAccountId` 호출이 추가되어 계정 삭제 시 FK null 처리가 이루어집니다.
- `DonationSavedEvent.java`는 스캐폴드만 존재하며 현재 미사용입니다.

## 최근 변경 메모 (v1.12.0)

- 공유 전적은 원본 `game_records`를 직접 노출하지 않고 `shared_game_records` 스냅샷으로 분리 관리합니다.
- `POST /record/share`는 24시간 안의 기존 공유 스냅샷이 있으면 같은 `shareId`를 재사용합니다.
- `POST /record/share/{shareId}`는 유효한 공유 스냅샷만 반환하며, 실질적인 만료 판정은 조회 시점의 `expires_at > NOW()` 조건으로 처리합니다.
- 만료된 공유 스냅샷 정리는 `SharedGameRecordCleanupScheduler`가 매시 정각 수행합니다.
- 계정 탈퇴 처리에는 공유 스냅샷 정리도 함께 포함됩니다.
## 최근 변경 메모 (v1.15.0)

- 신고 시스템이 추가됐습니다. `ReportController`, `ReportService`, `ReportMapper(.xml)`, `ReportVO`가 계정/후원 신고 생성과 사유별 집계를 처리합니다.
- 관리자 계정 관리 API가 추가됐습니다. `AdminAccountController`, `AdminAccountService`, `AccountMapper` 확장으로 신고된 계정 목록, 요약, 처리 완료, 프로필 검열, 닉네임 검열, 관리자 권한 부여를 제공합니다.
- 관리자 후원 모더레이션이 확장됐습니다. `DonationAdminController`, `DonationService`에서 후원 신고 요약, 처리 완료, 서포터 네임 검열/복구 토글, 후원 내용 검열, 후원 삭제를 처리합니다.
- 프로필 이미지 검열은 강한 블러 후 `censored-logo.png`를 중앙 오버레이하는 파이프라인을 사용합니다.
- `donations.is_account_linked_display` 컬럼과 `reports` 테이블이 추가됐습니다. 계정 연동 후원 신고는 `ACCOUNT`로 집계하되 `target_donation_id`를 함께 보존해 후원 카드 요약에도 반영합니다.
