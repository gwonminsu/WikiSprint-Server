package com.wikisprint.server.service;

import com.fasterxml.uuid.Generators;
import com.wikisprint.server.global.common.status.FileException;
import com.wikisprint.server.global.common.storage.FileStorageService;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.mapper.ConsentMapper;
import com.wikisprint.server.mapper.DonationMapper;
import com.wikisprint.server.mapper.GameRecordMapper;
import com.wikisprint.server.mapper.RankingMapper;
import com.wikisprint.server.mapper.SharedGameRecordMapper;
import com.wikisprint.server.vo.AccountVO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final String PROFILE_CATEGORY = "profile";
    private static final int DELETION_BATCH_SIZE = 100;
    private static final int PROFILE_BLUR_LONG_EDGE = 8;
    private static final int PROFILE_BLUR_KERNEL_SIZE = 21;
    private static final int PROFILE_BLUR_PASSES = 6;
    private static final String CENSORED_LOGO_PATH = "images/censored-logo.png";
    private static final double CENSORED_LOGO_CANVAS_RATIO = 0.42;
    private static final int CENSORED_NICK_MAX_RETRY = 20;

    private final AccountMapper accountMapper;
    private final FileStorageService fileStorage;
    private final GameRecordMapper gameRecordMapper;
    private final RankingMapper rankingMapper;
    private final ConsentMapper consentMapper;
    private final SharedGameRecordMapper sharedGameRecordMapper;
    private final DonationMapper donationMapper;
    private final NicknameGenerator nicknameGenerator;

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

        fileStorage.validateFile(file);

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
        String extension = fileStorage.getFileExtension(file.getOriginalFilename());
        String storedName = fileId + (extension.isEmpty() ? "" : "." + extension);

        String storagePath = fileStorage.buildStoragePath(accountUuid, accountUuid, PROFILE_CATEGORY, null);
        fileStorage.saveFile(file, storagePath, storedName);

        String uri = fileStorage.buildUri(accountUuid, accountUuid, PROFILE_CATEGORY, null, storedName);
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

    // 로컬 프로필 이미지를 강한 블러 이미지로 교체한다.
    @Transactional
    public String censorProfileImage(String accountUuid) throws IOException {
        AccountVO account = accountMapper.selectAccountByUuid(accountUuid);
        if (account == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }

        String profileImgUrl = account.getProfileImgUrl();
        if (profileImgUrl == null || profileImgUrl.isBlank()) {
            throw new IllegalArgumentException("검열할 프로필 이미지가 없습니다.");
        }
        if (profileImgUrl.startsWith("http://") || profileImgUrl.startsWith("https://")) {
            throw new IllegalArgumentException("외부 프로필 이미지는 서버에서 검열할 수 없습니다.");
        }

        Path sourcePath = Path.of(fileStorage.getStorageRoot(), profileImgUrl).normalize();
        BufferedImage sourceImage = ImageIO.read(sourcePath.toFile());
        if (sourceImage == null) {
            throw new IllegalArgumentException("프로필 이미지 파일을 읽을 수 없습니다.");
        }

        BufferedImage censoredImage = createCensoredProfileImage(sourceImage);
        String storedName = "censored-profile-" + Generators.timeBasedEpochGenerator().generate() + ".png";
        String storagePath = fileStorage.buildStoragePath(accountUuid, accountUuid, PROFILE_CATEGORY, null);
        File storageDirectory = new File(storagePath);
        if (!storageDirectory.exists() && !storageDirectory.mkdirs()) {
            throw new IOException("프로필 이미지 저장 경로를 만들 수 없습니다.");
        }

        File targetFile = new File(storageDirectory, storedName);
        ImageIO.write(censoredImage, "png", targetFile);

        String uri = fileStorage.buildUri(accountUuid, accountUuid, PROFILE_CATEGORY, null, storedName);
        accountMapper.updateProfileImgUrl(accountUuid, uri);
        deleteExistingProfileFile(accountUuid, profileImgUrl);
        log.info("CENSOR account profile image: {}", accountUuid);
        return uri;
    }

    // 계정 닉네임을 신규 가입 기본 닉네임 형식으로 검열한다.
    @Transactional
    public String censorNick(String accountUuid) {
        AccountVO account = accountMapper.selectAccountByUuid(accountUuid);
        if (account == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }

        for (int attempt = 0; attempt < CENSORED_NICK_MAX_RETRY; attempt++) {
            String nick = "⚠️" + nicknameGenerator.generateUniqueNickname(accountMapper) + "⚠️";
            if (accountMapper.checkExistedNick(nick)) {
                continue;
            }

            try {
                accountMapper.updateNick(accountUuid, nick);
                log.info("CENSOR account nick: {} -> {}", accountUuid, nick);
                return nick;
            } catch (DuplicateKeyException exception) {
                log.warn("검열 닉네임 중복, 재시도: {}", nick);
            }
        }

        throw new IllegalStateException("검열 닉네임을 생성할 수 없습니다.");
    }

    @Transactional
    public void grantAdmin(String accountUuid) {
        AccountVO account = accountMapper.selectAccountByUuid(accountUuid);
        if (account == null) {
            throw new IllegalArgumentException("계정을 찾을 수 없습니다.");
        }

        accountMapper.updateIsAdmin(accountUuid, true);
        log.info("GRANT admin role: {}", accountUuid);
    }

    private BufferedImage createCensoredProfileImage(BufferedImage sourceImage) throws IOException {
        BufferedImage censoredLogo = readCensoredLogo();
        int logoLongEdge = Math.max(censoredLogo.getWidth(), censoredLogo.getHeight());
        int minimumCanvasLongEdge = (int) Math.ceil(logoLongEdge * CENSORED_LOGO_CANVAS_RATIO);
        int canvasWidth = Math.max(sourceImage.getWidth(), minimumCanvasLongEdge);
        int canvasHeight = Math.max(sourceImage.getHeight(), minimumCanvasLongEdge);

        BufferedImage coverImage = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D coverGraphics = coverImage.createGraphics();
        coverGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        coverGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        drawImageCover(coverGraphics, sourceImage, canvasWidth, canvasHeight);
        coverGraphics.dispose();

        BufferedImage blurredImage = createStrongBlurImage(coverImage);

        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvasGraphics = canvas.createGraphics();
        canvasGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        canvasGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        canvasGraphics.drawImage(blurredImage, 0, 0, null);

        int targetLogoLongEdge = Math.max(1, (int) Math.round(Math.min(canvasWidth, canvasHeight) * CENSORED_LOGO_CANVAS_RATIO));
        double logoScale = Math.min(1.0, (double) targetLogoLongEdge / logoLongEdge);
        int logoWidth = Math.max(1, (int) Math.round(censoredLogo.getWidth() * logoScale));
        int logoHeight = Math.max(1, (int) Math.round(censoredLogo.getHeight() * logoScale));
        int logoX = (canvasWidth - logoWidth) / 2;
        int logoY = (canvasHeight - logoHeight) / 2;
        canvasGraphics.drawImage(censoredLogo, logoX, logoY, logoWidth, logoHeight, null);
        canvasGraphics.dispose();

        return canvas;
    }

    private BufferedImage readCensoredLogo() throws IOException {
        ClassPathResource resource = new ClassPathResource(CENSORED_LOGO_PATH);
        BufferedImage censoredLogo = ImageIO.read(resource.getInputStream());
        if (censoredLogo == null) {
            throw new IOException("검열 로고 이미지를 읽을 수 없습니다.");
        }
        return censoredLogo;
    }

    private void drawImageCover(Graphics2D graphics, BufferedImage image, int canvasWidth, int canvasHeight) {
        double scale = Math.max(
                (double) canvasWidth / image.getWidth(),
                (double) canvasHeight / image.getHeight()
        );
        int drawWidth = Math.max(1, (int) Math.ceil(image.getWidth() * scale));
        int drawHeight = Math.max(1, (int) Math.ceil(image.getHeight() * scale));
        int drawX = (canvasWidth - drawWidth) / 2;
        int drawY = (canvasHeight - drawHeight) / 2;
        graphics.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
    }

    private BufferedImage createStrongBlurImage(BufferedImage sourceImage) {
        int sourceWidth = sourceImage.getWidth();
        int sourceHeight = sourceImage.getHeight();
        int longEdge = Math.max(sourceWidth, sourceHeight);
        double scaleRatio = Math.min(1.0, (double) PROFILE_BLUR_LONG_EDGE / longEdge);
        int smallWidth = Math.max(1, (int) Math.round(sourceWidth * scaleRatio));
        int smallHeight = Math.max(1, (int) Math.round(sourceHeight * scaleRatio));

        BufferedImage smallImage = new BufferedImage(smallWidth, smallHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D smallGraphics = smallImage.createGraphics();
        smallGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        smallGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        smallGraphics.drawImage(sourceImage, 0, 0, smallWidth, smallHeight, null);
        smallGraphics.dispose();

        BufferedImage blurredImage = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D blurredGraphics = blurredImage.createGraphics();
        blurredGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        blurredGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        blurredGraphics.drawImage(smallImage, 0, 0, sourceWidth, sourceHeight, null);
        blurredGraphics.dispose();

        return applyRepeatedBoxBlur(blurredImage);
    }

    private BufferedImage applyRepeatedBoxBlur(BufferedImage sourceImage) {
        int kernelCellCount = PROFILE_BLUR_KERNEL_SIZE * PROFILE_BLUR_KERNEL_SIZE;
        float[] kernelData = new float[kernelCellCount];
        for (int index = 0; index < kernelCellCount; index++) {
            kernelData[index] = 1.0f / kernelCellCount;
        }

        ConvolveOp blurOp = new ConvolveOp(
                new Kernel(PROFILE_BLUR_KERNEL_SIZE, PROFILE_BLUR_KERNEL_SIZE, kernelData),
                ConvolveOp.EDGE_NO_OP,
                null
        );

        BufferedImage currentImage = sourceImage;
        for (int pass = 0; pass < PROFILE_BLUR_PASSES; pass++) {
            BufferedImage nextImage = new BufferedImage(
                    sourceImage.getWidth(),
                    sourceImage.getHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );
            blurOp.filter(currentImage, nextImage);
            currentImage = nextImage;
        }

        return currentImage;
    }

    // 기존 프로필 이미지 파일 삭제 (내부용)
    private void deleteExistingProfileFile(String accountUuid, String profileImgUrl) {
        try {
            String fullPath = fileStorage.getStorageRoot() + "/" + profileImgUrl;
            fileStorage.deleteFile(fullPath);
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
                String fullPath = fileStorage.getStorageRoot() + "/" + account.getProfileImgUrl();
                fileStorage.deleteFile(fullPath);
            } catch (Exception e) {
                log.warn("프로필 이미지 삭제 실패 (무시): {}", e.getMessage());
            }
        }

        // FK 순서로 하위 데이터 삭제
        sharedGameRecordMapper.deleteAllByAccountId(accountUuid);
        gameRecordMapper.deleteAllByAccountId(accountUuid);
        rankingMapper.deleteAllByAccountId(accountUuid);
        consentMapper.deleteAllByAccountId(accountUuid);
        donationMapper.clearWikiSprintAccountIdByAccountId(accountUuid);
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
