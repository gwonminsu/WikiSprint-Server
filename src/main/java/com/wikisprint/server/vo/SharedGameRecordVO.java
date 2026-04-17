package com.wikisprint.server.vo;

import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.LocalDateTime;

// 24시간 동안 유지되는 공유 전적 스냅샷
@Data
@Alias("SharedGameRecordVO")
public class SharedGameRecordVO {
    private String shareId;
    private String accountId;
    private String recordId;
    private String nick;
    private String profileImgUrl;
    private String targetWord;
    private String startDoc;
    private String navPath;
    private Long elapsedMs;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
