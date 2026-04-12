package com.wikisprint.server.service;

import com.fasterxml.uuid.Generators;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.wikisprint.server.dto.TokenDTO;
import com.wikisprint.server.global.common.auth.JwtTokenProvider;
import com.wikisprint.server.global.common.status.UnauthorizedException;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.vo.AccountVO;
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

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AccountMapper accountMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final RestTemplate restTemplate;
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
            // 자동 회원가입
            String accountUuid = "ACC-" + Generators.timeBasedEpochGenerator().generate().toString();
            String nick = (name != null && !name.isEmpty()) ? name : email.split("@")[0];

            account = new AccountVO();
            account.setUuid(accountUuid);
            account.setGoogleId(googleId);
            account.setEmail(email);
            account.setNick(nick);
            account.setProfileImgUrl(picture);
            account.setNationality(nationality);
            accountMapper.insertAccount(account);
            log.info("AUTO REGISTER SUCCESS google_id: {}, email: {}", googleId, email);
        }

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

    public AccountVO getAccountByUuid(String uuid) {
        return accountMapper.selectAccountByUuid(uuid);
    }
}
