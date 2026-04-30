package com.wikisprint.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Wikipedia REST API 연동 서비스
 * CC BY-SA 3.0 라이선스 콘텐츠 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikipediaService {

    private final RestTemplate restTemplate;

    private static final String USER_AGENT = "WikiSprint/1.0 (https://github.com/wikisprint; contact@wikisprint.com) RestTemplate";
    private static final Set<String> ALLOWED_LANGS = Set.of("ko", "en", "ja", "zh");

    // 캐시 TTL: 1시간 (밀리초)
    private static final long CACHE_TTL_MS = 60 * 60 * 1000L;
    // 캐시 최대 크기 — 초과 시 가장 오래된 엔트리 제거
    private static final int MAX_CACHE_SIZE = 500;

    // 캐시 엔트리 (값 + 생성 시각)
    private record CacheEntry(Object value, long createdAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }
    }

    // 문서 HTML 캐시 (키: lang:title)
    private final ConcurrentHashMap<String, CacheEntry> htmlCache = new ConcurrentHashMap<>();
    // 문서 요약 캐시 (키: lang:title)
    private final ConcurrentHashMap<String, CacheEntry> summaryCache = new ConcurrentHashMap<>();

    /**
     * 언어 코드 검증 후 Wikipedia API Base URL 반환
     * 허용되지 않은 언어 코드는 'ko'로 기본값 처리
     */
    private String getWikiApiBase(String lang) {
        String validLang = ALLOWED_LANGS.contains(lang) ? lang : "ko";
        return "https://" + validLang + ".wikipedia.org/api/rest_v1";
    }

    /**
     * Wikipedia API 공통 요청 헤더 생성
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);
        headers.set("Accept", "application/json");
        return headers;
    }

    /**
     * 지수 백오프 재시도 래퍼
     * 429(Rate Limit) 또는 네트워크/타임아웃 오류 시 최대 3회 재시도: 1초 → 2초 → 4초
     */
    private <T> T executeWithRetry(Supplier<T> apiCall) {
        int maxAttempts = 3;
        long delayMs = 1000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return apiCall.get();
            } catch (HttpClientErrorException e) {
                // 429 Rate Limit: 지수 백오프 후 재시도
                if (e.getStatusCode().value() == 429 && attempt < maxAttempts) {
                    log.warn("[WikipediaService] Rate limit 감지 (429), {}ms 후 재시도 ({}/{})", delayMs, attempt, maxAttempts);
                    sleep(delayMs);
                    delayMs *= 2;
                } else {
                    throw e;
                }
            } catch (ResourceAccessException e) {
                // 타임아웃/네트워크 오류: 재시도
                if (attempt < maxAttempts) {
                    log.warn("[WikipediaService] 네트워크 오류, {}ms 후 재시도 ({}/{}): {}", delayMs, attempt, maxAttempts, e.getMessage());
                    sleep(delayMs);
                    delayMs *= 2;
                } else {
                    throw e;
                }
            }
        }
        throw new RestClientException("최대 재시도 횟수 초과");
    }

    /**
     * 최대 크기를 초과할 경우 가장 오래된 엔트리를 제거하고 삽입
     */
    private void putWithSizeLimit(ConcurrentHashMap<String, CacheEntry> cache, String key, CacheEntry entry) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            cache.entrySet().stream()
                    .min(java.util.Comparator.comparingLong(e -> e.getValue().createdAt()))
                    .ifPresent(e -> cache.remove(e.getKey()));
        }
        cache.put(key, entry);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RestClientException("재시도 대기 중 인터럽트 발생");
        }
    }

    /**
     * 10분마다 만료된 캐시 엔트리 일괄 제거
     */
    @Scheduled(fixedRate = 3_600_000)
    public void evictExpiredCaches() {
        htmlCache.entrySet().removeIf(e -> e.getValue().isExpired());
        summaryCache.entrySet().removeIf(e -> e.getValue().isExpired());
        log.debug("[WikipediaService] 만료 캐시 정리 완료 — html: {}건, summary: {}건 남음", htmlCache.size(), summaryCache.size());
    }

    /**
     * 랜덤 문서 요약 조회 — 캐싱 없음 (랜덤 특성상 캐시 불필요)
     * GET /page/random/summary
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRandomSummary(String lang) {
        String url = getWikiApiBase(lang) + "/page/random/summary";
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        return executeWithRetry(() -> {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return (Map<String, Object>) response.getBody();
        });
    }

    /**
     * 문서 HTML 조회 — lang:title 키로 10분 캐싱
     * GET /page/html/{title}
     */
    @SuppressWarnings("unchecked")
    public String getArticleHtml(String title, String lang) {
        String cacheKey = lang + ":" + title;
        CacheEntry cached = htmlCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("[WikipediaService] HTML 캐시 히트: {}", cacheKey);
            return (String) cached.value();
        }

        String url = getWikiApiBase(lang) + "/page/html/" + title;
        HttpHeaders headers = buildHeaders();
        headers.set("Accept", "text/html; charset=utf-8");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String html = executeWithRetry(() -> {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        });

        putWithSizeLimit(htmlCache, cacheKey, new CacheEntry(html, System.currentTimeMillis()));
        return html;
    }

    /**
     * 문서 요약 조회 — lang:title 키로 10분 캐싱
     * GET /page/summary/{title}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getArticleSummary(String title, String lang) {
        String cacheKey = lang + ":" + title;
        CacheEntry cached = summaryCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("[WikipediaService] Summary 캐시 히트: {}", cacheKey);
            return (Map<String, Object>) cached.value();
        }

        String url = getWikiApiBase(lang) + "/page/summary/" + title;
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());

        Map<String, Object> summary = executeWithRetry(() -> {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return (Map<String, Object>) response.getBody();
        });

        putWithSizeLimit(summaryCache, cacheKey, new CacheEntry(summary, System.currentTimeMillis()));
        return summary;
    }
}
