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
├── controller/          # AuthController, AccountController, WikiController, AdminController
├── service/             # AuthService, AccountService, WikipediaService
├── mapper/              # AccountMapper, TargetWordMapper (MyBatis DAO)
├── vo/                  # AccountVO, TargetWordVO
├── dto/                 # GoogleLoginReqDTO, ApiResponse<T>
└── global/
    ├── config/          # SecurityConfig, GoogleOAuthConfig, RestTemplateConfig
    └── common/
        ├── auth/        # JwtTokenProvider, JwtAuthenticationFilter
        ├── status/      # 커스텀 예외
        └── util/        # FileStorageUtil
```

**MyBatis Mapper XML 위치:**
- `src/main/resources/mapper/user/` — AccountMapper
- `src/main/resources/mapper/game/` — TargetWordMapper

## 핵심 시스템

### 인증 플로우 (Google OAuth)

```
POST /auth/google (credential: Google ID Token)
  → GoogleIdTokenVerifier.verify()
  → payload에서 sub(google_id), email, name, picture 추출
  → accountMapper.selectAccountByGoogleId()
  → 기존 계정 없으면 자동 가입 (insertAccount)
  → JWT access token + refresh token 발급 및 반환
```

### 데이터베이스 스키마 (PostgreSQL)

- 스키마: `wikisprint` (connection-init-sql로 설정)
- 테이블: `accounts` (계정, google_id로 식별)
  - `account_id` VARCHAR(50) PK
  - `google_id` VARCHAR(255) UNIQUE
  - `email` VARCHAR(255)
  - `nick` VARCHAR(50)
  - `profile_img_url` VARCHAR(500)
  - `is_admin` BOOLEAN NOT NULL DEFAULT FALSE
  - `last_login`, `created_at`, `updated_at`
- 테이블: `target_words` (제시어)
  - `word_id` SERIAL PK
  - `word` VARCHAR(100)
  - `difficulty` SMALLINT (1: 쉬움, 2: 보통, 3: 어려움)
  - `lang` VARCHAR(5) (ko, en, ja)
  - `created_at` TIMESTAMP
  - UNIQUE(word, lang)

### 보안 설정

- CORS 허용: `http://localhost:5969` (프론트엔드)
- 공개 엔드포인트: `/auth/**`, `/error/**`, `/account/profile/image/**`, `/wiki/**`
- 보호된 엔드포인트: JWT Bearer 토큰 필요
- 관리자 엔드포인트 (`/admin/**`): JWT 인증 + `AdminController.resolveAdmin()` DB 레벨 `is_admin` 이중 검증

### 관리자 API (`/api/admin/**`)

| 엔드포인트 | 설명 | 인증 |
|---|---|---|
| `POST /admin/words/list` | 전체 제시어 목록 조회 | JWT + is_admin |
| `POST /admin/words/add` | 제시어 추가 (body: `{ word, difficulty, lang }`) | JWT + is_admin |
| `POST /admin/words/delete` | 제시어 삭제 (body: `{ wordId }`) | JWT + is_admin |

## 외부 의존성

- **PostgreSQL:** `127.0.0.1:5432/postgres` (스키마: wikisprint)
- **Google OAuth:** `GOOGLE_CLIENT_ID` 환경변수로 설정

## API 규칙

- 모든 엔드포인트는 POST 메서드 사용
- 요청/응답 본문은 JSON 형식
- 인증: `Authorization: Bearer {access_token}` 헤더
- 응답 래퍼: `ApiResponse<T>` (`{ data, message, auth }`)

## Claude Code 명령어

### patch note
버전을 생성하고 변경 내용을 PATCH.md 파일에 자동으로 작성합니다.

**실행 방법:** `patch note` 입력

**동작:**
1. PATCH.md에서 최신 버전 확인
2. git log/diff를 분석하여 변경 내용 파악
3. PATCH.md 최상단에 새 버전 항목 추가
4. 버전 사이 구분선: `=` 104개 × 3줄
5. 패치노트 작성 후 Git 커밋 및 푸시

**커밋 규칙:**
- 제목: `feat: 대표 변경사항 (vX.X.X)`
