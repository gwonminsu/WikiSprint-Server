package com.wikisprint.server.global.common.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

// 로그아웃한 Refresh 토큰의 jti를 만료 시각까지 보관한다.
// in-memory이므로 서버 재시작 시 초기화된다 — 기존 토큰은 다시 유효해지지만
// 로그아웃 직후 단기 재사용을 막는 것이 목적이므로 허용 가능한 트레이드오프.
@Component
public class TokenBlacklist {

    private final ConcurrentHashMap<String, Long> store = new ConcurrentHashMap<>();

    public void add(String jti, long expiryEpochMs) {
        store.put(jti, expiryEpochMs);
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null) return false;
        Long expiry = store.get(jti);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            store.remove(jti);
            return false;
        }
        return true;
    }

    // 1시간마다 만료된 항목 청소
    @Scheduled(fixedDelay = 3_600_000L)
    public void cleanup() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue() < now);
    }
}
