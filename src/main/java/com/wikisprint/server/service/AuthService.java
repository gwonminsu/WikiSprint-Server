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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AccountMapper accountMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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
            accountMapper.insertAccount(account);
            log.info("AUTO REGISTER SUCCESS google_id: {}, email: {}", googleId, email);
        }

        // JWT 발급
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(account.getUuid(), null, authorities);
        TokenDTO token = jwtTokenProvider.createAllToken(authToken);

        accountMapper.updateLastLoginAt(account.getUuid());
        log.info("GOOGLE LOGIN SUCCESS uuid: {}", account.getUuid());

        return Map.of(
                "uuid", account.getUuid(),
                "nick", account.getNick(),
                "email", account.getEmail(),
                "profile_img_url", account.getProfileImgUrl() != null ? account.getProfileImgUrl() : "",
                "token", token
        );
    }

    /**
     * refresh token으로 새 토큰 재발급
     */
    public TokenDTO reissueToken(String refreshToken) {
        try {
            jwtTokenProvider.validateRefreshToken(refreshToken);
        } catch (Exception e) {
            throw new UnauthorizedException("리프레시 토큰이 유효하지 않습니다.");
        }

        Authentication auth = jwtTokenProvider.getAuthentication(refreshToken, true);
        log.info("TOKEN REISSUE SUCCESS uuid: {}", auth.getName());
        return jwtTokenProvider.createAllToken(auth);
    }

    public AccountVO getAccountByUuid(String uuid) {
        return accountMapper.selectAccountByUuid(uuid);
    }
}
