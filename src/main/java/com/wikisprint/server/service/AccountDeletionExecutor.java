package com.wikisprint.server.service;

import com.wikisprint.server.global.common.storage.FileStorageService;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.mapper.ConsentMapper;
import com.wikisprint.server.mapper.DonationMapper;
import com.wikisprint.server.mapper.GameRecordMapper;
import com.wikisprint.server.mapper.RankingMapper;
import com.wikisprint.server.mapper.ReportMapper;
import com.wikisprint.server.mapper.SharedGameRecordMapper;
import com.wikisprint.server.vo.AccountVO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountDeletionExecutor {
    private static final Logger log = LoggerFactory.getLogger(AccountDeletionExecutor.class);

    private final AccountMapper accountMapper;
    private final FileStorageService fileStorage;
    private final GameRecordMapper gameRecordMapper;
    private final RankingMapper rankingMapper;
    private final ConsentMapper consentMapper;
    private final SharedGameRecordMapper sharedGameRecordMapper;
    private final DonationMapper donationMapper;
    private final ReportMapper reportMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteAccountInNewTransaction(String accountUuid) {
        deleteAccount(accountUuid);
    }

    @Transactional
    public void deleteAccount(String accountUuid) {
        AccountVO account = accountMapper.selectAccountByUuid(accountUuid);
        if (account == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }

        deleteProfileImage(account);
        unlinkRetainedRecords(accountUuid);
        deleteOwnedRecords(accountUuid);
        accountMapper.deleteAccount(accountUuid);

        log.info("ACCOUNT DELETED uuid: {}", accountUuid);
    }

    private void deleteProfileImage(AccountVO account) {
        if (account.getProfileImgUrl() == null || account.getProfileImgUrl().isEmpty()) {
            return;
        }

        try {
            String fullPath = fileStorage.getStorageRoot() + "/" + account.getProfileImgUrl();
            fileStorage.deleteFile(fullPath);
        } catch (Exception exception) {
            log.warn("프로필 이미지 삭제 실패 (무시): {}", exception.getMessage());
        }
    }

    private void unlinkRetainedRecords(String accountUuid) {
        donationMapper.unlinkAccountByAccountId(accountUuid);
        reportMapper.clearReporterAccountId(accountUuid);
        reportMapper.clearTargetAccountId(accountUuid);
        reportMapper.clearResolvedByAccountId(accountUuid);
    }

    private void deleteOwnedRecords(String accountUuid) {
        sharedGameRecordMapper.deleteAllByAccountId(accountUuid);
        gameRecordMapper.deleteAllByAccountId(accountUuid);
        rankingMapper.deleteAllByAccountId(accountUuid);
        consentMapper.deleteAllByAccountId(accountUuid);
    }
}
