package com.wikisprint.server.controller;

import com.wikisprint.server.dto.ApiResponse;
import com.wikisprint.server.mapper.TargetWordMapper;
import com.wikisprint.server.service.WikipediaService;
import com.wikisprint.server.vo.TargetWordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Wikipedia API 프록시 컨트롤러
 * 프론트엔드의 CORS 우회 및 캐싱을 위한 백엔드 경유 엔드포인트
 * 콘텐츠 출처: Wikipedia (CC BY-SA 3.0)
 */
@RestController
@RequestMapping("/wiki")
@RequiredArgsConstructor
public class WikiController {

    private final WikipediaService wikipediaService;
    private final TargetWordMapper targetWordMapper;

    /**
     * 랜덤 위키피디아 문서 요약 조회
     * GET /api/wiki/random?lang=ko
     */
    @GetMapping("/random")
    public ResponseEntity<?> getRandomArticle(@RequestParam(defaultValue = "ko") String lang) {
        try {
            Map<String, Object> summary = wikipediaService.getRandomSummary(lang);
            return ResponseEntity.ok(ApiResponse.success(summary));
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Wikipedia API 호출 실패: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("랜덤 문서 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 특정 문서 HTML 조회
     * GET /api/wiki/page/html/{title}?lang=ko
     */
    @GetMapping("/page/html/{title}")
    public ResponseEntity<?> getArticleHtml(@PathVariable String title,
                                             @RequestParam(defaultValue = "ko") String lang) {
        try {
            String html = wikipediaService.getArticleHtml(title, lang);
            return ResponseEntity.ok(ApiResponse.success(Map.of("html", html)));
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Wikipedia API 호출 실패: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("문서 HTML 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 랜덤 제시어 조회
     * GET /api/wiki/target/random?lang=ko
     */
    @GetMapping("/target/random")
    public ResponseEntity<?> getRandomTargetWord(@RequestParam(defaultValue = "ko") String lang) {
        try {
            TargetWordVO word = targetWordMapper.selectRandomWord(lang);
            if (word == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("등록된 제시어가 없습니다."));
            }
            return ResponseEntity.ok(ApiResponse.success(word));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("제시어 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 특정 문서 요약 조회
     * GET /api/wiki/page/summary/{title}?lang=ko
     */
    @GetMapping("/page/summary/{title}")
    public ResponseEntity<?> getArticleSummary(@PathVariable String title,
                                                @RequestParam(defaultValue = "ko") String lang) {
        try {
            Map<String, Object> summary = wikipediaService.getArticleSummary(title, lang);
            return ResponseEntity.ok(ApiResponse.success(summary));
        } catch (RestClientException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.error("Wikipedia API 호출 실패: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("문서 요약 조회 중 오류가 발생했습니다."));
        }
    }
}
