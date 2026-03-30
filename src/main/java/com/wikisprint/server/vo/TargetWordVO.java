package com.wikisprint.server.vo;

import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.LocalDateTime;

@Data
@Alias("TargetWordVO")
public class TargetWordVO {

    private Integer wordId;

    private String word;

    /** 난이도: 1(쉬움), 2(보통), 3(어려움) */
    private Short difficulty;

    /** 언어 코드: ko, en, ja */
    private String lang;

    private LocalDateTime createdAt;
}
