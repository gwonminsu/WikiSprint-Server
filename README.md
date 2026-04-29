<div align="center">

# WikiSprint Server

**WikiSprint의 인증, 기록, 랭킹, 공유, 후원 기능을 제공하는 Spring Boot API 서버입니다.**

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.7-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring_Security-6.x-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![MyBatis](https://img.shields.io/badge/MyBatis-3.0.5-C0392B?style=flat-square)](https://mybatis.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Latest-336791?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![JWT](https://img.shields.io/badge/JWT-JJWT_0.11.5-000000?style=flat-square&logo=jsonwebtokens&logoColor=white)](https://github.com/jwtk/jjwt)
[![Version](https://img.shields.io/badge/version-v1.16.1-brightgreen?style=flat-square)](./PATCH.md)

</div>

---

## 소개

WikiSprint Server는 WikiSprint 게임의 상태를 저장하고, 프론트엔드가 사용하는 JSON API를 제공합니다.  
Google OAuth 로그인, JWT 인증, 게임 기록 관리, 랭킹 집계, 공유 링크 발급, 후원 처리, 관리자 기능이 이 서버를 중심으로 동작합니다.

### 서버가 담당하는 역할

| 영역 | 설명 |
|---|---|
| 인증 | Google 로그인 검증, 회원가입, 토큰 재발급, 로그아웃 |
| 계정 | 프로필, 닉네임, 국적, 탈퇴 요청 처리 |
| 기록 | 시작, 경로 업데이트, 완료, 포기, 최근 기록 조회 |
| 랭킹 | 기간별 랭킹 집계와 랭킹 알림 데이터 제공 |
| 공유 | `shareId` 기반 공유 링크 발급과 조회 |
| 후원 | Ko-fi 웹훅, 국내 후원 요청, 관리자 후원 확인 |
| 관리자 | 신고, 계정, 후원 데이터 운영 API |

---

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 3.5.7 |
| 보안 | Spring Security, JJWT 0.11.5 |
| 데이터 접근 | MyBatis 3.0.5 |
| 데이터베이스 | PostgreSQL |
| 외부 인증 | Google API Client |

---

## 구조

```text
Controller -> Service -> Mapper(MyBatis) -> PostgreSQL
```

### 패키지 개요

```text
com.wikisprint.server/
├─ controller/
├─ service/
├─ mapper/
├─ dto/
├─ vo/
├─ event/
└─ global/
   ├─ config/
   └─ common/
      ├─ auth/
      ├─ filter/
      ├─ status/
      ├─ storage/
      └─ GlobalExceptionHandler
```

### 핵심 서비스

| 컴포넌트 | 설명 |
|---|---|
| `GameRecordService` | 기록 생성, 완료, 포기, stale 정리, 공유 기록 연결 |
| `RankingService` | 기간별 랭킹 계산과 최근 랭킹 알림 조회 |
| `DonationService` | Ko-fi 웹훅, 계좌이체 후원 요청, 관리자 후원 처리 |
| `FileStorageService` | 프로필 이미지 저장 경로 추상화 |

---

## API 개요

기본 정보는 다음과 같습니다.

- 서버 포트: `8585`
- 컨텍스트 패스: `/api`
- PostgreSQL 스키마: `wikisprint`

### 주요 엔드포인트

| 범주 | 경로 |
|---|---|
| 인증 | `/api/auth/**` |
| 계정 | `/api/account/**` |
| 기록 | `/api/record/**` |
| 랭킹 | `/api/ranking/**` |
| 후원 | `/api/donations/**`, `/api/webhook/**` |
| 관리자 | `/api/admin/**` |
| 위키 조회 | `/api/wiki/**` |

### 대표 기능 흐름

| 기능 | 설명 |
|---|---|
| Google 로그인 | ID Token 또는 OAuth code 검증 후 JWT 발급 |
| 게임 기록 | 시작, 경로 업데이트, 완료/포기 처리 후 기록 저장 |
| 랭킹 알림 | 최근 진입 및 추월 이벤트를 조회용 데이터로 제공 |
| 후원 알림 | `alertCreatedAt`을 포함해 클라이언트가 재생 기준 시점을 판단할 수 있게 제공 |
| 공유 링크 | `shareId` 발급 후 제한 시간 내 조회 가능 |

### 응답 형식 예시

```json
{
  "data": {},
  "message": "SUCCESS",
  "auth": {
    "accessToken": "...",
    "refreshToken": "..."
  }
}
```

---

## 데이터 메모

주요 테이블은 다음과 같습니다.

| 테이블 | 설명 |
|---|---|
| `accounts` | 계정, 통계, 최고 기록, 관리자 여부 |
| `target_words` | 게임 난이도별 목표 단어 |
| `game_records` | 진행 중, 완료, 포기 기록 |
| `ranking_records` | 기간별 랭킹 스냅샷 |
| `shared_game_records` | 공유 링크용 기록 매핑 |
| `donations` | Ko-fi 및 국내 후원 데이터 |
| `reports` | 신고 데이터 |
| `consent_records` | 약관 동의 이력 |

초기 스키마는 [schema-init.sql](./src/main/resources/schema-init.sql) 기준으로 관리합니다.

---

## 보안 및 운영 메모

### 공개 경로

- `/auth/**`
- `/error/**`
- `/account/profile/image/**`
- `/wiki/**`
- `/ranking/**`
- `/donations/**`
- `/webhook/**`

### 보호 경로

- 그 외 경로는 JWT Bearer 토큰이 필요합니다.
- 관리자 API는 JWT 인증과 DB 기준 `is_admin` 검증을 함께 사용합니다.

### 레이트 리밋

- `/webhook/kofi`: 분당 60회
- `/donations/**`: 분당 30회

### 후원 응답 메모

- 후원 응답 DTO에는 `alertCreatedAt`이 포함됩니다.
- 실시간 알림과 재생 알림은 이벤트 생성 시각을 그대로 사용합니다.
- 일반 후원 목록 응답은 `receivedAt` 기반 시각을 fallback으로 제공합니다.

---

## 실행 방법

### 요구 사항

- Java 17 이상
- Gradle 8 이상
- PostgreSQL 14 이상

### 실행

```bash
cd WikiSprint-Server
./gradlew bootRun
```

### 빌드 및 테스트

```bash
./gradlew build
./gradlew test
./gradlew clean build
```

---

## 환경 설정

주요 값은 `local.properties`로 주입합니다.

```properties
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
DB_PASSWORD=your-db-password
JWT_SECRET=your-jwt-secret-key
APP_STORAGE_ROOT=./storage
KOFI_WEBHOOK_TOKEN=your-kofi-token
```

### 기본 설정 예시

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
```

---

## 파일 저장소 메모

- 프로필 이미지는 `FileStorageService` 추상화 아래에서 관리합니다.
- 기본 저장 경로는 `./storage`입니다.
- 운영 환경에서는 `APP_STORAGE_ROOT`로 외부 경로를 지정할 수 있습니다.
- DB에는 절대 경로 대신 상대 경로를 저장합니다.

---

## 프론트엔드 연동

- 프론트 개발 서버: `http://localhost:5969`
- 기본 API 서버: `http://localhost:8585`

프론트 구조와 사용자 흐름은 [WikiSprint-Web README](../WikiSprint-Web/README.md)에서 확인할 수 있습니다.

---

## 문서

- 변경 이력: [PATCH.md](./PATCH.md)
- 작업 메모: [CLAUDE.md](./CLAUDE.md)

---

<div align="center">

**WikiSprint** · Built with Spring Boot & PostgreSQL

</div>
