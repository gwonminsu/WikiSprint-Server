package com.wikisprint.server.service;

import com.fasterxml.uuid.Generators;
import com.wikisprint.server.global.common.status.FileException;
import com.wikisprint.server.global.common.util.FileStorageUtil;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.mapper.ConsentMapper;
import com.wikisprint.server.mapper.GameRecordMapper;
import com.wikisprint.server.mapper.RankingMapper;
import com.wikisprint.server.mapper.SharedGameRecordMapper;
import com.wikisprint.server.vo.AccountVO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final String PROFILE_CATEGORY = "profile";
    private static final int DELETION_BATCH_SIZE = 100;

    private final AccountMapper accountMapper;
    private final FileStorageUtil fileStorageUtil;
    private final GameRecordMapper gameRecordMapper;
    private final RankingMapper rankingMapper;
    private final ConsentMapper consentMapper;
    private final SharedGameRecordMapper sharedGameRecordMapper;

    // 닉네임 변경
    @Transactional
    public void updateNick(String accountUuid, String newNick) {
        AccountVO account = accountMapper.selectAccountByUuid(accountUuid);
        if (account == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }

        if (account.getNick().equals(newNick)) {
            throw new IllegalArgumentException("현재 닉네임과 동일합니다.");
        }

        if (accountMapper.checkExistedNick(newNick)) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        accountMapper.updateNick(accountUuid, newNick);
        log.info("UPDATE account nick: {} -> {}", account.getNick(), newNick);
    }

    // 국적 변경 (null 허용 - 무국적 복원)
    @Transactional
    public void updateNationality(String accountUuid, String nationality) {
        AccountVO account = accountMapper.selectAccountByUuid(accountUuid);
        if (account == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }

        if (nationality != null && nationality.length() != 2) {
            throw new IllegalArgumentException("유효하지 않은 국적 코드입니다.");
        }

        accountMapper.updateNationality(accountUuid, nationality);
        log.info("UPDATE account nationality: {} -> {}", account.getNationality(), nationality);
    }

    // 프로필 이미지 업로드/변경
    @Transactional
    public String updateProfileImage(String accountUuid, MultipartFile file) throws IOException {
        AccountVO account = accountMapper.selectAccountByUuid(accountUuid);
        if (account == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }

        fileStorageUtil.validateFile(file);

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new FileException("이미지 파일만 업로드 가능합니다.");
        }

        // 기존 프로필 이미지 삭제
        if (account.getProfileImgUrl() != null && !account.getProfileImgUrl().isEmpty()) {
            deleteExistingProfileFile(accountUuid, account.getProfileImgUrl());
        }

        // 새 파일 저장
        String fileId = "FIL-" + Generators.timeBasedEpochGenerator().generate().toString();
        String extension = fileStorageUtil.getFileExtension(file.getOriginalFilename());
        String storedName = fileId + (extension.isEmpty() ? "" : "." + extension);

        String storagePath = fileStorageUtil.buildStoragePath(accountUuid, accountUuid, PROFILE_CATEGORY, null);
        fileStorageUtil.saveFile(file, storagePath, storedName);

        String uri = fileStorageUtil.buildUri(accountUuid, accountUuid, PROFILE_CATEGORY, null, storedName);
        accountMapper.updateProfileImgUrl(accountUuid, uri);
        log.info("UPDATE account profile_img_url: {}", uri);

        return uri;
    }

    // 프로필 이미지 제거
    @Transactional
    public void removeProfileImage(String accountUuid) {
        AccountVO account = accountMapper.selectAccountByUuid(accountUuid);
        if (account == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }

        if (account.getProfileImgUrl() == null || account.getProfileImgUrl().isEmpty()) {
            throw new IllegalArgumentException("제거할 프로필 이미지가 없습니다.");
        }

        deleteExistingProfileFile(accountUuid, account.getProfileImgUrl());
        accountMapper.updateProfileImgUrl(accountUuid, null);
        log.info("REMOVE account profile_img_url: {}", accountUuid);
    }

    // 기존 프로필 이미지 파일 삭제 (내부용)
    private void deleteExistingProfileFile(String accountUuid, String profileImgUrl) {
        try {
            String fullPath = fileStorageUtil.getStoragePath() + "/" + profileImgUrl;
            fileStorageUtil.deleteFile(fullPath);
            log.info("DELETE existing profile file: {}", profileImgUrl);
        } catch (Exception e) {
            log.warn("Failed to delete existing profile image: {}", e.getMessage());
        }
    }

    // 계정 조회
    @Transactional(readOnly = true)
    public AccountVO getAccountByUuid(String accountUuid) {
        return accountMapper.selectAccountByUuid(accountUuid);
    }

    // 회원탈퇴 요청 (7일 유예)
    @Transactional
    public void requestDeletion(String accountUuid) {
        AccountVO account = accountMapper.selectAccountByUuid(accountUuid);
        if (account == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }

        accountMapper.updateDeletionRequestedAt(accountUuid, LocalDateTime.now());
        log.info("DELETION REQUESTED uuid: {}", accountUuid);
    }

    // 계정 즉시 삭제 - 공유 스냅샷 포함 하위 데이터를 FK 순서대로 정리한다.
    @Transactional
    public void deleteAccountImmediately(String accountUuid) {
        AccountVO account = accountMapper.selectAccountByUuid(accountUuid);
        if (account == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }

        // 프로필 이미지 파일 삭제 (파일 없어도 무시)
        if (account.getProfileImgUrl() != null && !account.getProfileImgUrl().isEmpty()) {
            try {
                String fullPath = fileStorageUtil.getStoragePath() + "/" + account.getProfileImgUrl();
                fileStorageUtil.deleteFile(fullPath);
            } catch (Exception e) {
                log.warn("프로필 이미지 삭제 실패 (무시): {}", e.getMessage());
            }
        }

        // FK 순서로 하위 데이터 삭제
        sharedGameRecordMapper.deleteAllByAccountId(accountUuid);
        gameRecordMapper.deleteAllByAccountId(accountUuid);
        rankingMapper.deleteAllByAccountId(accountUuid);
        consentMapper.deleteAllByAccountId(accountUuid);
        accountMapper.deleteAccount(accountUuid);

        log.info("ACCOUNT DELETED uuid: {}", accountUuid);
    }

    // 만료된 탈퇴 계정 배치 삭제 (스케줄러에서 호출)
    @Transactional
    public int deleteExpiredAccounts() {
        List<AccountVO> expiredAccounts = accountMapper.selectExpiredDeletionAccounts(DELETION_BATCH_SIZE);

        int deletedCount = 0;
        for (AccountVO account : expiredAccounts) {
            try {
                deleteAccountImmediately(account.getUuid());
                deletedCount++;
            } catch (Exception e) {
                log.error("만료 계정 삭제 실패 uuid: {}, error: {}", account.getUuid(), e.getMessage());
            }
        }

        return deletedCount;
    }
}
