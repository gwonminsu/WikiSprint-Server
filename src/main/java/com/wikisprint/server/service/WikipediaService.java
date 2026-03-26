package com.wikisprint.server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Wikipedia REST API 연동 서비스
 * CC BY-SA 3.0 라이선스 콘텐츠 제공
 */
@Service
@RequiredArgsConstructor
public class WikipediaService {

    private final RestTemplate restTemplate;

    private static final String WIKI_API_BASE = "https://ko.wikipedia.org/api/rest_v1";
    private static final String USER_AGENT = "WikiSprint/1.0 (https://github.com/wikisprint; contact@wikisprint.com) RestTemplate";

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
     * 랜덤 문서 요약 조회
     * GET /page/random/summary
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRandomSummary() {
        String url = WIKI_API_BASE + "/page/random/summary";
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        return (Map<String, Object>) response.getBody();
    }

    /**
     * 문서 HTML 조회
     * GET /page/html/{title}
     */
    public String getArticleHtml(String title) {
        String url = WIKI_API_BASE + "/page/html/" + title;
        HttpHeaders headers = buildHeaders();
        headers.set("Accept", "text/html; charset=utf-8");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return response.getBody();
    }

    /**
     * 문서 요약 조회
     * GET /page/summary/{title}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getArticleSummary(String title) {
        String url = WIKI_API_BASE + "/page/summary/" + title;
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        return (Map<String, Object>) response.getBody();
    }
}
