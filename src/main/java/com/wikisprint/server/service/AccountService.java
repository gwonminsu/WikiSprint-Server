package com.wikisprint.server.service;

import com.wikisprint.server.global.common.status.FileException;
import com.wikisprint.server.global.common.util.FileStorageUtil;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.vo.AccountVO;
import com.fasterxml.uuid.Generators;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountMapper accountMapper;
    private final FileStorageUtil fileStorageUtil;
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private static final String PROFILE_CATEGORY = "profile";

    /**
     * 닉네임 변경
     */
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

    /**
     * 프로필 이미지 업로드/변경
     */
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

    /**
     * 프로필 이미지 제거
     */
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

    /**
     * 기존 프로필 이미지 파일 삭제 (내부용)
     */
    private void deleteExistingProfileFile(String accountUuid, String profileImgUrl) {
        try {
            String fullPath = fileStorageUtil.getStoragePath() + "/" + profileImgUrl;
            fileStorageUtil.deleteFile(fullPath);
            log.info("DELETE existing profile file: {}", profileImgUrl);
        } catch (Exception e) {
            log.warn("Failed to delete existing profile image: {}", e.getMessage());
        }
    }

    /**
     * 계정 조회
     */
    @Transactional(readOnly = true)
    public AccountVO getAccountByUuid(String accountUuid) {
        return accountMapper.selectAccountByUuid(accountUuid);
    }
}
