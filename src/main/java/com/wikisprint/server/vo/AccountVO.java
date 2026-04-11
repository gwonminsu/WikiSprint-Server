package com.wikisprint.server.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.LocalDateTime;

@Data
@Alias("AccountVO")
public class AccountVO {

    private String uuid;

    @JsonProperty("google_id")
    private String googleId;

    private String email;

    private String nick;

    @JsonProperty("profile_img_url")
    private String profileImgUrl;

    // ISO 3166-1 alpha-2 국가 코드 (예: "KR", null = 무국적)
    private String nationality;

    @JsonProperty("is_admin")
    private Boolean isAdmin;

    private LocalDateTime lastLogin;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // 누적 통계
    private Integer totalGames;
    private Integer totalClears;
    private Integer totalAbandons;

    // 최고 기록 (밀리초 단위, 클리어한 게임 중 최단 시간. null이면 클리어 기록 없음)
    private Long bestRecord;
}
