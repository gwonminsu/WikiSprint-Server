package com.wikisprint.server.event;

import com.wikisprint.server.vo.DonationVO;
import lombok.Getter;

// 후원 저장 완료 후 발행하는 이벤트
@Getter
public class DonationSavedEvent {

    private final DonationVO donation;

    public DonationSavedEvent(DonationVO donation) {
        this.donation = donation;
    }
}
