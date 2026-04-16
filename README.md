<div align="center">

# ⚙️ WikiSprint Server

**WikiSprint 백엔드 — Spring Boot + MyBatis + PostgreSQL**

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.7-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring_Security-6.x-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![MyBatis](https://img.shields.io/badge/MyBatis-3.0.5-C0392B?style=flat-square)](https://mybatis.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Latest-336791?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![JWT](https://img.shields.io/badge/JWT-JJWT_0.11.5-000000?style=flat-square&logo=jsonwebtokens&logoColor=white)](https://github.com/jwtk/jjwt)
[![Version](https://img.shields.io/badge/version-v1.11.1-brightgreen?style=flat-square)](./PATCH.md)

</div>

---

## 🎮 WikiSprint이란?

**WikiSprint**는 유튜버 **침착맨**이 소개한 나무위키 스피드런에서 영감을 받아 만들어진 위키 스피드런 게임입니다.

[Wikipedia REST API](https://ko.wikipedia.org/api/rest_v1/) (CC BY-SA 3.0)를 활용해 위키피디아 문서를 제공하며, **제시어**가 주어지면 무작위 위키피디아 문서에서 출발해 문서 내 링크만을 따라 목표 문서에 가장 빠르게 도달하는 것이 목표입니다. 백엔드가 Wikipedia API를 경유하여 캐싱 및 데이터 가공을 담당합니다.

### 게임 규칙

- 게임 시작 버튼을 누르는 순간 타이머가 시작됩니다
- 문서 내 링크만을 이용해 제시어 문서까지 이동해야 합니다
- 비로그인 상태로도 플레이 가능, 랭킹 등록·댓글은 로그인 필요

> 📄 콘텐츠 출처: [Wikipedia](https://ko.wikipedia.org/) (CC BY-SA 3.0)

---

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| 🔐 Google OAuth 인증 | Google ID Token 검증 및 자동 회원가입 |
| 🎫 JWT 토큰 관리 | Access / Refresh Token 발급 및 자동 갱신 |
| 👤 계정 관리 API | 닉네임 변경, 프로필 이미지 업로드/삭제 |
| 🗄 PostgreSQL 연동 | MyBatis ORM + `wikisprint` 스키마 |
| 🔒 Spring Security | JWT 필터 기반 인증, CORS 설정 |
| 🖼 파일 스토리지 | 프로필 이미지 로컬 저장 및 정적 서빙 |
| 🏷 전적 난이도 응답 | 최근 전적 조회 시 제시어 난이도(`difficulty`)를 함께 반환 |

---

## 🛠 기술 스택

| 카테고리 | 기술 | 버전 |
|---------|------|------|
| 언어 | Java | 17 |
| 프레임워크 | Spring Boot | 3.5.7 |
| 보안 | Spring Security | (Boot 관리) |
| ORM | MyBatis | 3.0.5 |
| 데이터베이스 | PostgreSQL | 최신 |
| 인증 토큰 | JJWT | 0.11.5 |
| Google OAuth | google-api-client | 2.7.0 |
| 유틸 | Lombok | (Boot 관리) |

---

## 🏗 아키텍처

```
Controller → Service → Mapper (MyBatis) → PostgreSQL
                           ↓
                     VO / DTO types
```

### 패키지 구조

```
com.wikisprint.server/
├── controller/          # AuthController, AccountController, WikiController, AdminController, GameRecordController, RankingController
├── service/             # AuthService, AccountService, WikipediaService, GameRecordService, RankingService
├── mapper/              # AccountMapper, TargetWordMapper, GameRecordMapper, RankingMapper (MyBatis DAO)
├── vo/                  # AccountVO, TargetWordVO, GameRecordVO, RankingRecordVO
├── dto/                 # GoogleLoginReqDTO, ApiResponse<T>, TokenDTO
└── global/
    ├── config/          # SecurityConfig, GoogleOAuthConfig, RestTemplateConfig
    └── common/
        ├── auth/        # JwtTokenProvider, JwtAuthenticationFilter
        ├── status/      # 커스텀 예외 (FileException, UnauthorizedException)
        ├── util/        # FileStorageUtil
        └── GlobalExceptionHandler
```

---

## 🔌 API 엔드포인트

> 기본 경로: `/api` · 포트: `8585`

### 인증 (Auth)

| 엔드포인트 | 메서드 | 인증 | 설명 |
|-----------|--------|------|------|
| `/api/auth/google` | POST | ❌ | Google ID Token으로 로그인 / 자동 회원가입 |
| `/api/auth/token/refresh` | POST | Refresh Token | Access Token 재발급 |

### 계정 (Account)

| 엔드포인트 | 메서드 | 인증 | 설명 |
|-----------|--------|------|------|
| `/api/account/me` | GET | ✅ Bearer | 내 계정 정보 조회 |
| `/api/account/detail` | POST | ✅ Bearer | 특정 계정 조회 |
| `/api/account/nick/update` | POST | ✅ Bearer | 닉네임 변경 |
| `/api/account/profile/upload` | POST (multipart) | ✅ Bearer | 프로필 이미지 업로드 |
| `/api/account/profile/remove` | POST | ✅ Bearer | 프로필 이미지 삭제 |
| `/api/account/profile/image/**` | GET | ❌ | 프로필 이미지 정적 서빙 |

### 위키 (Wiki)

| 엔드포인트 | 메서드 | 인증 | 설명 |
|-----------|--------|------|------|
| `/api/wiki/random` | GET | ❌ | 랜덤 Wikipedia 문서 요약 |
| `/api/wiki/page/html/{title}` | GET | ❌ | 문서 HTML 반환 |
| `/api/wiki/page/summary/{title}` | GET | ❌ | 문서 요약 반환 |
| `/api/wiki/target/random` | GET | ❌ | DB에서 랜덤 제시어 조회 |

### 전적 (Record)

| 엔드포인트 | 메서드 | 인증 | 설명 |
|-----------|--------|------|------|
| `/api/record/start` | POST | ✅ Bearer | 게임 전적 시작 |
| `/api/record/update-path` | POST | ✅ Bearer | 이동 경로 갱신 |
| `/api/record/complete` | POST | ✅ Bearer | 클리어 처리 |
| `/api/record/abandon` | POST | ✅ Bearer | 포기 처리 |
| `/api/record/list` | POST | ✅ Bearer | 최근 전적과 통계 조회 (`difficulty` 포함) |

### 응답 형식

```json
{
  "data": { ... },
  "message": "SUCCESS",
  "auth": { "accessToken": "...", "refreshToken": "..." }
}
```

---

## 🗄 데이터베이스 스키마

스키마: `wikisprint`

### accounts

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `account_id` | VARCHAR(50) PK | UUID 기반 계정 ID |
| `google_id` | VARCHAR(255) UNIQUE | Google 계정 식별자 (`sub`) |
| `email` | VARCHAR(255) | 이메일 주소 |
| `nick` | VARCHAR(50) | 닉네임 |
| `profile_img_url` | VARCHAR(500) | 프로필 이미지 경로 |
| `nationality` | VARCHAR(2) | ISO 3166-1 alpha-2 국가 코드 (null = 무국적) |
| `is_admin` | BOOLEAN | 관리자 여부 (기본값 false) |
| `total_games` | INTEGER | 총 게임 수 |
| `total_clears` | INTEGER | 총 클리어 수 |
| `total_abandons` | INTEGER | 총 포기 수 |
| `best_record` | BIGINT | 최단 클리어 시간 ms (null = 기록 없음) |
| `last_login` | TIMESTAMP | 최근 로그인 시각 |
| `created_at` | TIMESTAMP | 계정 생성 시각 |
| `updated_at` | TIMESTAMP | 마지막 수정 시각 |

### target_words

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `word_id` | SERIAL PK | 제시어 ID |
| `word` | VARCHAR(100) | 제시어 텍스트 |
| `difficulty` | SMALLINT | 난이도 (1: 쉬움, 2: 보통, 3: 어려움) |
| `lang` | VARCHAR(5) | 언어 코드 (ko, en, ja) |
| `created_at` | TIMESTAMP | 등록 시각 |

### game_records

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `record_id` | VARCHAR(50) PK | 전적 ID |
| `account_id` | VARCHAR(50) FK | 계정 ID |
| `target_word` | VARCHAR(100) | 제시어 |
| `start_doc` | VARCHAR(300) | 시작 문서 |
| `nav_path` | TEXT | 이동 경로 JSON |
| `elapsed_ms` | BIGINT | 경과 시간 |
| `status` | VARCHAR(20) | `in_progress`, `cleared`, `abandoned` |
| `last_article` | VARCHAR(300) | 진행 중 마지막 문서 |
| `played_at` | TIMESTAMP | 플레이 시각 |

> 최근 전적 조회에서는 `target_words`를 참조해 `difficulty`를 함께 응답합니다.

> 초기화 스크립트: [`src/main/resources/schema-init.sql`](./src/main/resources/schema-init.sql)

---

## 🔐 인증 플로우

```
POST /api/auth/google  { credential: "Google ID Token" }
         │
         ▼
  GoogleIdTokenVerifier.verify()
         │
         ▼
  payload 추출 (google_id, email, name, picture)
         │
         ▼
  accountMapper.selectAccountByGoogleId()
         │
    ┌────┴────┐
  존재      없음
    │         │
    │    insertAccount()  ← 자동 회원가입
    │         │
    └────┬────┘
         ▼
  JWT access + refresh 토큰 발급 및 반환
```

---

## 🚀 시작하기

### 요구사항

- Java 17+
- Gradle 8+
- PostgreSQL (포트 5432)

### PostgreSQL 설정

```sql
-- 스키마 생성
CREATE SCHEMA IF NOT EXISTS wikisprint;

-- 테이블 생성은 schema-init.sql 참고
```

### 환경 변수 설정

`local.properties` 또는 환경변수로 설정:

```properties
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
DB_PASSWORD=your-db-password
JWT_SECRET=your-jwt-secret-key
```

### 빌드 및 실행

```bash
# 서버 실행 (포트 8585)
./gradlew bootRun

# 빌드
./gradlew build

# 클린 빌드
./gradlew clean build

# 테스트 실행
./gradlew test
```

---

## ⚙️ 주요 설정 (`application.yaml`)

```yaml
server:
  port: 8585
  servlet:
    context-path: /api

spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/postgres
    hikari:
      connection-init-sql: "SET search_path TO wikisprint"

google:
  client-id: ${GOOGLE_CLIENT_ID}
```

---

## 🔒 보안 설정

| 설정 | 내용 |
|------|------|
| CORS 허용 오리진 | `http://localhost:5969` |
| 공개 엔드포인트 | `/auth/**`, `/error/**`, `/account/profile/image/**`, `/wiki/**`, `/ranking/**` |
| 보호 엔드포인트 | JWT Bearer 토큰 필요 |
| 인증 헤더 | `Authorization: Bearer {access_token}` |

---

## 🔗 프론트엔드 연동

→ [WikiSprint-Web README](../WikiSprint-Web/README.md)

---

## 📜 패치 노트

최신 변경사항은 [PATCH.md](./PATCH.md)를 참고하세요.

---

<div align="center">

**WikiSprint** — Built with ❤️ using Spring Boot & PostgreSQL

</div>
