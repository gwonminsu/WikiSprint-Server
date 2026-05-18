package com.wikisprint.server.global.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wikisprint.server.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 후원 관련 공개 엔드포인트에 간단한 분당 요청 제한을 적용한다.
@Component
public class SimpleRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS   = 60_000L;
    private static final int WEBHOOK_LIMIT    = 60;
    private static final int DONATION_LIMIT   = 30;
    private static final int WIKI_LIMIT       = 300;
    private static final int RECORD_LIMIT     = 120;
    private static final int REISSUE_LIMIT    = 30;
    private static final int REPORT_LIMIT     = 20;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ArrayDeque<Long>> requestHistory = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        int limit = resolveLimit(requestUri);

        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = buildClientKey(request, requestUri);
        if (isRateLimited(clientKey, limit)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.error("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int resolveLimit(String requestUri) {
        if (requestUri == null) return 0;

        if (requestUri.endsWith("/webhook/kofi"))                               return WEBHOOK_LIMIT;
        if (requestUri.startsWith("/donations") ||
            requestUri.startsWith("/api/donations"))                            return DONATION_LIMIT;
        if (requestUri.startsWith("/wiki") ||
            requestUri.startsWith("/api/wiki"))                                 return WIKI_LIMIT;
        if (requestUri.startsWith("/record") ||
            requestUri.startsWith("/api/record"))                               return RECORD_LIMIT;
        if (requestUri.endsWith("/auth/token/refresh"))                          return REISSUE_LIMIT;
        if (requestUri.startsWith("/reports") ||
            requestUri.startsWith("/api/reports"))                              return REPORT_LIMIT;

        return 0;
    }

    // X-Forwarded-For 헤더를 우선 읽어 프록시(ALB/CloudFront) 뒤에서도 실제 클라이언트 IP를 키로 사용한다.
    private String buildClientKey(HttpServletRequest request, String requestUri) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String clientIp = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return clientIp + "::" + requestUri;
    }

    private boolean isRateLimited(String clientKey, int limit) {
        long now = Instant.now().toEpochMilli();
        ArrayDeque<Long> history = requestHistory.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());

        synchronized (history) {
            while (!history.isEmpty() && now - history.peekFirst() > WINDOW_MILLIS) {
                history.pollFirst();
            }

            if (history.size() >= limit) {
                return true;
            }

            history.addLast(now);
            return false;
        }
    }
}
