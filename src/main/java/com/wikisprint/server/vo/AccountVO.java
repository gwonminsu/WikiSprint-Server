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

    @JsonProperty("is_admin")
    private Boolean isAdmin;

    private LocalDateTime lastLogin;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // 누적 통계
    private Integer totalGames;
    private Integer totalClears;
    private Integer totalAbandons;
}
