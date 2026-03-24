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
