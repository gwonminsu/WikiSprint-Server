package com.wikisprint.server.service;

import com.fasterxml.uuid.Generators;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.wikisprint.server.dto.ConsentItemDTO;
import com.wikisprint.server.dto.TokenDTO;
import com.wikisprint.server.global.common.ConsentConstants;
import com.wikisprint.server.global.common.auth.JwtTokenProvider;
import com.wikisprint.server.global.common.status.UnauthorizedException;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.mapper.ConsentMapper;
import com.wikisprint.server.vo.AccountVO;
import com.wikisprint.server.vo.ConsentRecordVO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AccountMapper accountMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final RestTemplate restTemplate;
    private final NicknameGenerator nicknameGenerator;
    private final ConsentMapper consentMapper;
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.client-secret}")
    private String googleClientSecret;

    /**
     * Google id_token 검증 후 로그인 또는 자동 가입 처리
     * @return 계정 정보 + TokenDTO가 담긴 Map
     */
    @Transactional
    public Map<String, Object> googleLogin(String idTokenString) {
        // Google id_token 검증
        GoogleIdToken idToken;
        try {
            idToken = googleIdTokenVerifier.verify(idTokenString);
        } catch (Exception e) {
            throw new UnauthorizedException("Google 토큰 검증 중 오류가 발생했습니다.");
        }

        if (idToken == null) {
            throw new UnauthorizedException("유효하지 않은 Google 토큰입니다.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");
        String nationality = (String) payload.get("nationality");

        // 기존 계정 조회
        AccountVO account = accountMapper.selectAccountByGoogleId(googleId);

        if (account == null) {
            // 신규 유저: 계정을 생성하지 않고 Google 정보만 반환
            // 실제 계정 생성은 프론트에서 약관 동의 완료 후 /auth/register 호출 시 수행됨
            Map<String, Object> newUserResult = new java.util.HashMap<>();
            newUserResult.put("is_new_user", true);
            newUserResult.put("is_deletion_pending", false);
            // iOS code flow에서 프론트가 register 호출 시 credential을 재사용하기 위해 id_token_string 반환
            // TODO: 향후 보안 강화를 위해 임시 signup session token 방식으로 교체 가능
            //       현재는 Google ID Token 자체를 그대로 전달하는 방식 사용
            newUserResult.put("id_token_string", idTokenString);
            newUserResult.put("email", email);
            // 토큰 없이 반환 → AuthController에서 auth 필드 없이 응답
            log.info("NEW USER DETECTED google_id: {}, email: {}", googleId, email);
            return newUserResult;
        }

        // 탈퇴 요청 중인 계정 처리: 토큰 발급 없이 탈퇴 대기 상태 반환
        if (account.getDeletionRequestedAt() != null) {
            Map<String, Object> pendingResult = new java.util.HashMap<>();
            pendingResult.put("is_new_user", false);
            pendingResult.put("is_deletion_pending", true);
            // 영구 삭제 예정 시각 (탈퇴 요청일 + 7일)
            pendingResult.put("deletion_scheduled_at",
                account.getDeletionRequestedAt().plusDays(7).toString());
            // iOS code flow에서 탈퇴 취소 시 credential 재사용을 위해 id_token_string 반환
            // TODO: 향후 임시 session token 방식으로 교체 가능
            pendingResult.put("id_token_string", idTokenString);
            // 토큰 미발급 → 탈퇴 상태 계정이 정상 인증되는 것을 방지
            log.info("DELETION PENDING ACCOUNT login attempt uuid: {}", account.getUuid());
            return pendingResult;
        }

        // 정상 로그인 처리
        // JWT 발급 (관리자인 경우 ROLE_ADMIN 추가)
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (Boolean.TRUE.equals(account.getIsAdmin())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(account.getUuid(), null, authorities);
        TokenDTO token = jwtTokenProvider.createAllToken(authToken);

        accountMapper.updateLastLoginAt(account.getUuid());
        log.info("GOOGLE LOGIN SUCCESS uuid: {}", account.getUuid());

        // Map.of()는 null value를 허용하지 않으므로 HashMap 사용 (nationality가 null일 수 있음)
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("uuid", account.getUuid());
        result.put("nick", account.getNick());
        result.put("email", account.getEmail());
        result.put("profile_img_url", account.getProfileImgUrl() != null ? account.getProfileImgUrl() : "");
        result.put("is_admin", Boolean.TRUE.equals(account.getIsAdmin()));
        result.put("nationality", account.getNationality());
        result.put("is_new_user", false);
        result.put("is_deletion_pending", false);
        result.put("token", token);
        return result;
    }

    /**
     * refresh token으로 새 토큰 재발급
     * refresh token에는 auth claim이 없으므로, DB에서 계정을 다시 조회하여 권한을 재구성한다.
     */
    public TokenDTO reissueToken(String refreshToken) {
        try {
            jwtTokenProvider.validateRefreshToken(refreshToken);
        } catch (Exception e) {
            throw new UnauthorizedException("리프레시 토큰이 유효하지 않습니다.");
        }

        Authentication auth = jwtTokenProvider.getAuthentication(refreshToken, true);
        String uuid = auth.getName();

        // DB에서 계정을 다시 조회하여 최신 권한 재구성 (refresh token에는 auth claim 없음)
        AccountVO account = accountMapper.selectAccountByUuid(uuid);
        if (account == null) {
            throw new UnauthorizedException("계정을 찾을 수 없습니다.");
        }

        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (Boolean.TRUE.equals(account.getIsAdmin())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(uuid, null, authorities);

        log.info("TOKEN REISSUE SUCCESS uuid: {}", uuid);
        return jwtTokenProvider.createAllToken(authToken);
    }

    /**
     * iOS OAuth2 authorization code를 id_token으로 교환 후 로그인 처리
     * Google 토큰 엔드포인트에 code를 전달하고 받은 id_token으로 기존 로그인 로직 재사용
     */
    @Transactional
    public Map<String, Object> googleLoginWithCode(String code, String redirectUri) {
        // Google 토큰 엔드포인트에 code 교환 요청
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", googleClientId);
        body.add("client_secret", googleClientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResponse = restTemplate.postForObject(
                "https://oauth2.googleapis.com/token",
                request,
                Map.class
        );

        if (tokenResponse == null || !tokenResponse.containsKey("id_token")) {
            throw new UnauthorizedException("Google code 교환 실패: id_token이 없습니다.");
        }

        String idToken = (String) tokenResponse.get("id_token");
        // 기존 id_token 검증 + 로그인 로직 재사용
        return googleLogin(idToken);
    }

    /**
     * 신규 회원가입 완료 처리
     * 약관 동의 완료 후 프론트에서 호출. 계정 생성 + 동의 이력 저장을 단일 트랜잭션으로 처리.
     *
     * @param idTokenString Google ID Token (googleLogin에서 반환한 id_token_string)
     * @param consents 동의한 항목의 타입 목록 (동의한 항목만 전달)
     * @return 로그인 성공과 동일한 계정 정보 + JWT 토큰 Map
     */
    @Transactional
    public Map<String, Object> register(String idTokenString, List<ConsentItemDTO> consents) {
        // Google id_token 재검증
        GoogleIdToken idToken;
        try {
            idToken = googleIdTokenVerifier.verify(idTokenString);
        } catch (Exception e) {
            throw new UnauthorizedException("Google 토큰 검증 중 오류가 발생했습니다.");
        }
        if (idToken == null) {
            throw new UnauthorizedException("유효하지 않은 Google 토큰입니다.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String picture = (String) payload.get("picture");
        String nationality = (String) payload.get("nationality");

        // [중복 가입 방어] 동일 Google 계정이 이미 존재하면 새 계정을 생성하지 않고 기존 계정으로 로그인 처리
        // 가입 버튼 중복 클릭이나 재시도 요청에도 중복 계정 생성이 발생하지 않도록 보장
        AccountVO existingAccount = accountMapper.selectAccountByGoogleId(googleId);
        if (existingAccount != null) {
            log.info("REGISTER: 이미 가입된 계정. 기존 계정으로 로그인 처리 uuid: {}", existingAccount.getUuid());
            return buildLoginResult(existingAccount);
        }

        // [서비스 레벨 필수 동의 검증] 컨트롤러가 아닌 서비스에서 수행
        Set<String> consentTypeSet = consents != null
                ? consents.stream()
                .map(ConsentItemDTO::getType)
                .filter(type -> type != null && !type.isBlank())
                .collect(Collectors.toSet())
                : Set.of();
        for (String requiredType : ConsentConstants.REQUIRED_TYPES) {
            if (!consentTypeSet.contains(requiredType)) {
                throw new IllegalArgumentException("필수 동의 항목이 누락되었습니다: " + requiredType);
            }
        }

        // 계정 생성 (자동 닉네임 생성)
        String accountUuid = "ACC-" + Generators.timeBasedEpochGenerator().generate().toString();
        String nick = nicknameGenerator.generateUniqueNickname(accountMapper);

        AccountVO newAccount = new AccountVO();
        newAccount.setUuid(accountUuid);
        newAccount.setGoogleId(googleId);
        newAccount.setEmail(email);
        newAccount.setNick(nick);
        newAccount.setProfileImgUrl(picture);
        newAccount.setNationality(nationality);
        accountMapper.insertAccount(newAccount);
        log.info("REGISTER SUCCESS uuid: {}, email: {}, nick: {}", accountUuid, email, nick);

        // 동의한 항목만 consent_records에 저장 (동의하지 않은 항목은 row 생성 안 함)
        if (consents != null) {
            for (ConsentItemDTO item : consents) {
                if (item == null || item.getType() == null || item.getType().isBlank()) {
                    continue;
                }

                ConsentRecordVO consent = new ConsentRecordVO();
                consent.setAccountId(accountUuid);
                consent.setConsentType(item.getType());

                // 프론트 version이 오면 우선 사용, 없으면 서버 기본값 사용
                String version = (item.getVersion() != null && !item.getVersion().isBlank())
                        ? item.getVersion()
                        : resolveConsentVersion(item.getType());

                consent.setConsentVersion(version);
                consentMapper.insertConsent(consent);
            }
        }
        log.info("CONSENT SAVED account: {}, types: {}", accountUuid, consentTypeSet);

        // 가입 완료 후 즉시 로그인 처리 (JWT 발급)
        newAccount = accountMapper.selectAccountByUuid(accountUuid);
        return buildLoginResult(newAccount);
    }

    /**
     * 탈퇴 취소 처리 (Google ID Token으로 본인 확인 후 deletion_requested_at = NULL)
     * 토큰 미발급 상태에서도 호출 가능하도록 /auth/** 하위 permitAll 엔드포인트에서 사용
     *
     * @param idTokenString Google ID Token
     * @return 정상 로그인과 동일한 계정 정보 + JWT 토큰 Map
     */
    @Transactional
    public Map<String, Object> cancelDeletion(String idTokenString) {
        // Google id_token 검증
        GoogleIdToken idToken;
        try {
            idToken = googleIdTokenVerifier.verify(idTokenString);
        } catch (Exception e) {
            throw new UnauthorizedException("Google 토큰 검증 중 오류가 발생했습니다.");
        }
        if (idToken == null) {
            throw new UnauthorizedException("유효하지 않은 Google 토큰입니다.");
        }

        String googleId = idToken.getPayload().getSubject();
        AccountVO account = accountMapper.selectAccountByGoogleId(googleId);
        if (account == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }
        if (account.getDeletionRequestedAt() == null) {
            throw new IllegalArgumentException("탈퇴 요청 중인 계정이 아닙니다.");
        }

        // 탈퇴 요청 취소: deletion_requested_at = NULL
        accountMapper.updateDeletionRequestedAt(account.getUuid(), null);
        log.info("DELETION CANCELLED uuid: {}", account.getUuid());

        // 정상 로그인 처리
        account.setDeletionRequestedAt(null);
        return buildLoginResult(account);
    }

    /**
     * 로그인 성공 결과 Map 빌드 (JWT 발급 포함)
     * 정상 로그인, register, cancelDeletion에서 공통 사용
     */
    private Map<String, Object> buildLoginResult(AccountVO account) {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (Boolean.TRUE.equals(account.getIsAdmin())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(account.getUuid(), null, authorities);
        TokenDTO token = jwtTokenProvider.createAllToken(authToken);

        accountMapper.updateLastLoginAt(account.getUuid());

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("uuid", account.getUuid());
        result.put("nick", account.getNick());
        result.put("email", account.getEmail());
        result.put("profile_img_url", account.getProfileImgUrl() != null ? account.getProfileImgUrl() : "");
        result.put("is_admin", Boolean.TRUE.equals(account.getIsAdmin()));
        result.put("nationality", account.getNationality());
        result.put("is_new_user", false);
        result.put("is_deletion_pending", false);
        result.put("token", token);
        return result;
    }

    /**
     * 동의 타입에 해당하는 약관 버전 반환 (ConsentConstants에서 관리)
     */
    private String resolveConsentVersion(String consentType) {
        return switch (consentType) {
            case ConsentConstants.TYPE_TERMS_OF_SERVICE       -> ConsentConstants.VERSION_TERMS_OF_SERVICE;
            case ConsentConstants.TYPE_PRIVACY_POLICY         -> ConsentConstants.VERSION_PRIVACY_POLICY;
            case ConsentConstants.TYPE_AGE_VERIFICATION       -> ConsentConstants.VERSION_AGE_VERIFICATION;
            case ConsentConstants.TYPE_MARKETING_NOTIFICATION -> ConsentConstants.VERSION_MARKETING_NOTIFICATION;
            default -> "v1.0";
        };
    }

    public AccountVO getAccountByUuid(String uuid) {
        return accountMapper.selectAccountByUuid(uuid);
    }
}
