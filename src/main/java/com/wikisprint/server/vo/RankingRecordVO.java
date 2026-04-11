package com.wikisprint.server.vo;

import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 랭킹 기록 Value Object
@Data
@Alias("RankingRecordVO")
public class RankingRecordVO {
    private Long id;              // PK (SERIAL)
    private String accountId;     // FK → accounts.account_id
    private String nickname;      // JOIN: accounts.nick
    private String profileImageUrl; // JOIN: accounts.profile_img_url
    private String nationality;   // JOIN: accounts.nationality (ISO 3166-1 alpha-2, null = 무국적)

    private String periodType;    // daily | weekly | monthly
    private LocalDate periodBucket; // 기간 시작일 (KST 기준)
    private String difficulty;    // all | easy | normal | hard

    private Long elapsedMs;       // 클리어 시간 (밀리초)
    private String targetWord;    // 제시어
    private String startDoc;      // 시작 문서
    private Integer pathLength;   // 경로 길이 (방문 문서 수)

    private LocalDateTime createdAt; // 등록 시각
}
