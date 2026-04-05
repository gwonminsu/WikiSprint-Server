package com.wikisprint.server.global.common.auth;

import com.wikisprint.server.dto.TokenDTO;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {
    private final Key accessKey;
    private final Key refreshKey;

    public JwtTokenProvider(@Value("${jwt.access-key}") String accessSecretKey, @Value("${jwt.refresh-key}") String refreshSecretKey) {
        byte[] accessKeyBytes = Decoders.BASE64.decode(accessSecretKey);
        this.accessKey = Keys.hmacShaKeyFor(accessKeyBytes);

        byte[] refreshKeyBytes = Decoders.BASE64.decode(refreshSecretKey);
        this.refreshKey = Keys.hmacShaKeyFor(refreshKeyBytes);
    }

    public TokenDTO createAllToken(Authentication authentication) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        long now = (new Date()).getTime();

        String accessToken = Jwts.builder()
                .setSubject(authentication.getName())
                .claim("auth", authorities)
                .setExpiration(new Date(now + 1800000)) // 30분
                .signWith(accessKey, SignatureAlgorithm.HS256)
                .compact();

        String refreshToken = Jwts.builder()
                .setSubject(authentication.getName())
                .setExpiration(new Date(now + 1209600000))
                .signWith(refreshKey, SignatureAlgorithm.HS256)
                .compact();

        return TokenDTO.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public TokenDTO createAccessToken(Authentication authentication) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        long now = (new Date()).getTime();

        String accessToken = Jwts.builder()
                .setSubject(authentication.getName())
                .claim("auth", authorities)
                .setExpiration(new Date(now + 30000)) // 30초
                .signWith(accessKey, SignatureAlgorithm.HS256)
                .compact();

        return TokenDTO.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .build();
    }

    public TokenDTO createRefreshToken(Authentication authentication) {
        long now = (new Date()).getTime();

        String refreshToken = Jwts.builder()
                .setSubject(authentication.getName())
                .setExpiration(new Date(now + 1209600000))
                .signWith(refreshKey, SignatureAlgorithm.HS256)
                .compact();

        return TokenDTO.builder()
                .grantType("Bearer")
                .refreshToken(refreshToken)
                .build();
    }

    public Authentication getAuthentication(String token, boolean isRefresh) {
        // Bearer 접두사 제거
        String actualToken = resolveToken(token);

        Claims claims;
        if (isRefresh) {
            claims = Jwts.parserBuilder().setSigningKey(refreshKey).build().parseClaimsJws(actualToken).getBody();
        } else {
            claims = Jwts.parserBuilder().setSigningKey(accessKey).build().parseClaimsJws(actualToken).getBody();
        }

        String authClaims = claims.get("auth", String.class);
        if (isRefresh && (authClaims == null || authClaims.isEmpty())) {
            authClaims = "";
        }

        Collection<? extends GrantedAuthority> authorities = Arrays.stream(authClaims.split(","))
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        UserDetails principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    public boolean validateAccessToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(accessKey).build().parseClaimsJws(token);
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            throw new RuntimeException("Invalid JWT: " + e.getMessage());
        } catch (ExpiredJwtException e) {
            throw e; // ExpiredJwtException을 그대로 던져서 필터에서 처리
        } catch (UnsupportedJwtException e) {
            throw new RuntimeException("Unsupported JWT: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid arguments provided to JWT parsing: " + e.getMessage());
        }
        return true;
    }

    public boolean validateRefreshToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(refreshKey).build().parseClaimsJws(token);
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {

            throw new RuntimeException("Invalid JWT: " + e.getMessage());
        } catch (ExpiredJwtException e) {

            throw new RuntimeException("Expired JWT: " + e.getMessage());
        } catch (UnsupportedJwtException e) {

            throw new RuntimeException("Unsupported JWT: " + e.getMessage());
        } catch (IllegalArgumentException e) {

            throw new RuntimeException("Invalid arguments provided to JWT parsing: " + e.getMessage());
        }
        return true;
    }

    // Bearer 접두사 제거
    private String resolveToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return bearerToken;
    }
}
