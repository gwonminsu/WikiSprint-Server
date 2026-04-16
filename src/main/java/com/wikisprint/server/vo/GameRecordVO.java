package com.wikisprint.server.vo;

import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.LocalDateTime;

// 게임 전적 Value Object
@Data
@Alias("GameRecordVO")
public class GameRecordVO {
    private String recordId;       // PK (REC-{UUID})
    private String accountId;      // FK → accounts.account_id
    private String targetWord;     // 제시어
    private Short difficulty;      // 제시어 난이도 (1: 쉬움, 2: 보통, 3: 어려움)
    private String startDoc;       // 시작 문서 제목
    private String navPath;        // JSON 배열 문자열 (방문 경로)
    private Long elapsedMs;        // 경과 시간 (밀리초, 클리어 시에만 설정)
    private String status;         // in_progress | cleared | abandoned
    private String lastArticle;    // 마지막 도달 문서 (in_progress 추적용)
    private LocalDateTime playedAt;
    private LocalDateTime createdAt;
}
