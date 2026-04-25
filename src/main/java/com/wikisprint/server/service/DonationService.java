package com.wikisprint.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.uuid.Generators;
import com.wikisprint.server.dto.AccountTransferDonationCreateRequestDTO;
import com.wikisprint.server.dto.DonationResponseDTO;
import com.wikisprint.server.dto.PendingAccountTransferDonationResponseDTO;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.mapper.DonationMapper;
import com.wikisprint.server.vo.AccountVO;
import com.wikisprint.server.vo.DonationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

// Ko-fi 웹훅 처리와 후원 조회를 담당한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class DonationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE_KOFI = "kofi";
    private static final String SOURCE_ACCOUNT_TRANSFER = "account transfer";
    private static final String TYPE_DONATION = "Donation";
    private static final String TYPE_PENDING_TRANSFER = "PendingTransfer";
    private static final String CURRENCY_KRW = "KRW";
    private static final int LATEST_DONATION_LIMIT = 20;
    private static final int RECENT_ALERT_MINUTES = 10;
    private static final int MAX_ALERT_REPLAY_EVENTS = 200;
    private static final int MAX_TYPE_LENGTH = 30;
    private static final int MAX_SUPPORTER_NAME_LENGTH = 100;
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_COFFEE_COUNT = 100;
    private static final int KRW_PER_COFFEE = 2000;
    private static final Set<String> ALLOWED_CURRENCIES = Set.of(
            "KRW", "USD", "EUR", "JPY", "GBP", "CAD", "AUD"
    );

    private final DonationMapper donationMapper;
    private final AccountMapper accountMapper;
    private final Deque<DonationAlertReplayEvent> alertReplayEvents = new ConcurrentLinkedDeque<>();

    @Value("${kofi.webhook-enabled:false}")
    private boolean webhookEnabled;

    @Value("${kofi.webhook-token:}")
    private String webhookToken;

    @Transactional
    public KofiWebhookResult processKofiWebhook(String verificationToken, String rawData) {
        if (!webhookEnabled) {
            log.info("Ko-fi webhook skipped: disabled");
            return KofiWebhookResult.SKIPPED_DISABLED;
        }

        if (!isTokenValid(verificationToken)) {
            log.warn("Ko-fi webhook rejected: invalid token");
            throw new InvalidWebhookTokenException();
        }

        if (isBlank(rawData)) {
            throw new InvalidWebhookPayloadException("웹훅 데이터가 비어 있습니다.");
        }

        JsonNode rootNode = parseJson(rawData);
        if (rootNode == null || rootNode.isNull()) {
            throw new InvalidWebhookPayloadException("웹훅 data JSON 파싱에 실패했습니다.");
        }

        String kofiMessageId = readRequiredText(rootNode, "message_id", "message_id는 필수입니다.");
        if (donationMapper.existsByKofiMessageId(kofiMessageId)) {
            log.info("Ko-fi webhook deduplicated: messageId={}", kofiMessageId);
            return KofiWebhookResult.DUPLICATE;
        }

        String type = validateType(defaultIfBlank(readText(rootNode, "type"), TYPE_DONATION));
        String supporterName = validateLength(readText(rootNode, "from_name"), MAX_SUPPORTER_NAME_LENGTH, "후원자 이름");
        String message = validateLength(readText(rootNode, "message"), MAX_MESSAGE_LENGTH, "후원 메시지");
        String currency = validateCurrency(readRequiredText(rootNode, "currency", "currency는 필수입니다."));
        long amountCents = parseAmountMinorUnits(readRequiredText(rootNode, "amount", "amount는 필수입니다."));
        boolean isAnonymous = !readBoolean(rootNode, "is_public", true);
        String email = readText(rootNode, "email");
        String kofiAccountId = firstNonBlank(readText(rootNode, "account_id"), readText(rootNode, "transaction_id"));
        AccountVO linkedAccount = resolveWikiSprintAccount(email);

        DonationVO donation = new DonationVO();
        donation.setDonationId(generateDonationId());
        donation.setSource(SOURCE_KOFI);
        donation.setKofiAccountId(kofiAccountId);
        donation.setWikisprintAccountId(linkedAccount == null ? null : linkedAccount.getUuid());
        donation.setKofiMessageId(kofiMessageId);
        donation.setType(type);
        donation.setSupporterName(supporterName);
        donation.setMessage(message);
        donation.setIsAccountLinkedDisplay(resolveAccountLinkedDisplay(linkedAccount, supporterName, isAnonymous));
        donation.setAmountCents(amountCents);
        donation.setCurrency(currency);
        donation.setIsAnonymous(isAnonymous);
        donation.setReceivedAt(LocalDateTime.now());
        donation.setCreatedAt(LocalDateTime.now());

        donationMapper.insertDonation(donation);
        log.info("Ko-fi webhook saved: donationId={}, messageId={}", donation.getDonationId(), kofiMessageId);
        return KofiWebhookResult.SAVED;
    }

    @Transactional
    public String createAccountTransferDonation(AccountVO requester, AccountTransferDonationCreateRequestDTO request) {
        AccountVO resolvedRequester = requester;
        if (requester != null && requester.getUuid() != null && isBlank(requester.getNick())) {
            resolvedRequester = accountMapper.selectAccountByUuid(requester.getUuid());
        }

        int coffeeCount = validateCoffeeCount(request.getCoffeeCount());
        String remitterName = validateLength(requiredText(request.getRemitterName(), "입금자명은 필수입니다."), MAX_SUPPORTER_NAME_LENGTH, "입금자명");
        String message = validateLength(request.getMessage(), MAX_MESSAGE_LENGTH, "후원 메시지");
        String nickname = validateLength(request.getNickname(), MAX_SUPPORTER_NAME_LENGTH, "닉네임");
        boolean requestedAnonymous = Boolean.TRUE.equals(request.getAnonymous());

        ResolvedSupporter resolvedSupporter = resolveSupporter(resolvedRequester, nickname, requestedAnonymous);
        LocalDateTime now = LocalDateTime.now();

        DonationVO donation = new DonationVO();
        donation.setDonationId(generateDonationId());
        donation.setSource(SOURCE_ACCOUNT_TRANSFER);
        donation.setKofiAccountId(remitterName);
        donation.setWikisprintAccountId(resolvedRequester == null ? null : resolvedRequester.getUuid());
        donation.setKofiMessageId("KOFI-" + Generators.timeBasedEpochGenerator().generate());
        donation.setType(TYPE_PENDING_TRANSFER);
        donation.setSupporterName(resolvedSupporter.supporterName());
        donation.setMessage(message);
        donation.setIsAccountLinkedDisplay(resolveAccountLinkedDisplay(
                resolvedRequester,
                resolvedSupporter.supporterName(),
                resolvedSupporter.anonymous()
        ));
        donation.setAmountCents(calculateAccountTransferAmount(coffeeCount));
        donation.setCurrency(CURRENCY_KRW);
        donation.setIsAnonymous(resolvedSupporter.anonymous());
        donation.setReceivedAt(now);
        donation.setCreatedAt(now);

        donationMapper.insertDonation(donation);
        log.info("Account transfer donation requested: donationId={}, accountId={}", donation.getDonationId(), donation.getWikisprintAccountId());
        return donation.getDonationId();
    }

    @Transactional(readOnly = true)
    public List<DonationResponseDTO> getLatestDonations() {
        return donationMapper.selectLatestDonations(LATEST_DONATION_LIMIT)
                .stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DonationResponseDTO> getRecentAlertDonations() {
        return donationMapper.selectRecentDonations(LocalDateTime.now().minusMinutes(RECENT_ALERT_MINUTES))
                .stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DonationResponseDTO> getRecentAlertReplayDonations() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(RECENT_ALERT_MINUTES);
        pruneAlertReplayEvents(cutoff);

        List<DonationAlertReplayEvent> events = new ArrayList<>(alertReplayEvents);
        return events.stream()
                .filter(event -> !event.createdAt().isBefore(cutoff))
                .map(event -> {
                    DonationVO donation = donationMapper.selectDonationById(event.donationId());
                    if (donation == null) {
                        return null;
                    }
                    DonationResponseDTO response = toResponseDto(donation);
                    response.setAlertId(event.alertId());
                    return response;
                })
                .filter(response -> response != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DonationResponseDTO> getAllDonations() {
        return donationMapper.selectAllDonations()
                .stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public DonationResponseDTO getDonationById(String donationId) {
        DonationVO donation = donationMapper.selectDonationById(donationId);
        if (donation == null) {
            return null;
        }
        return toResponseDto(donation);
    }

    @Transactional(readOnly = true)
    public List<PendingAccountTransferDonationResponseDTO> getPendingAccountTransferDonations() {
        return donationMapper.selectPendingAccountTransfers()
                .stream()
                .map(this::toPendingResponseDto)
                .toList();
    }

    @Transactional
    public void confirmAccountTransferDonation(String donationId) {
        int updatedRows = donationMapper.confirmAccountTransferDonation(donationId);
        if (updatedRows <= 0) {
            throw new DonationNotFoundException();
        }
    }

    @Transactional(readOnly = true)
    public DonationResponseDTO createDonationAlertReplay(String donationId, String adminAccountId) {
        DonationVO donation = donationMapper.selectDonationById(donationId);
        if (donation == null) {
            throw new DonationNotFoundException();
        }

        DonationAlertReplayEvent event = new DonationAlertReplayEvent(
                generateAlertReplayId(),
                donationId,
                adminAccountId,
                LocalDateTime.now()
        );
        alertReplayEvents.addLast(event);
        pruneAlertReplayEvents(LocalDateTime.now().minusMinutes(RECENT_ALERT_MINUTES));
        trimAlertReplayEvents();

        DonationResponseDTO response = toResponseDto(donation);
        response.setAlertId(event.alertId());
        return response;
    }

    @Transactional
    public void censorSupporterName(String donationId) {
        DonationVO donation = donationMapper.selectDonationById(donationId);
        if (donation == null) {
            throw new DonationNotFoundException();
        }

        String nextSupporterName = "BadNameSupporter";
        if (Boolean.TRUE.equals(donation.getIsAccountLinkedDisplay())
                && "BadNameSupporter".equals(donation.getSupporterName())
                && !isBlank(donation.getAccountNick())) {
            nextSupporterName = donation.getAccountNick().trim();
        }

        int updatedRows = donationMapper.updateSupporterName(donationId, nextSupporterName);
        if (updatedRows <= 0) {
            throw new DonationNotFoundException();
        }
    }

    @Transactional
    public void censorDonationMessage(String donationId) {
        int updatedRows = donationMapper.updateDonationMessage(donationId, "censored content");
        if (updatedRows <= 0) {
            throw new DonationNotFoundException();
        }
    }

    @Transactional
    public void deleteDonation(String donationId) {
        int deletedRows = donationMapper.deleteDonation(donationId);
        if (deletedRows <= 0) {
            throw new DonationNotFoundException();
        }
    }

    DonationResponseDTO toResponseDto(DonationVO donation) {
        boolean isAnonymous = Boolean.TRUE.equals(donation.getIsAnonymous());

        return DonationResponseDTO.builder()
                .alertId(donation.getDonationId())
                .donationId(donation.getDonationId())
                .source(donation.getSource())
                .accountId(donation.getWikisprintAccountId())
                .accountNick(donation.getAccountNick())
                .accountProfileImgUrl(isAnonymous ? null : donation.getAccountProfileImgUrl())
                .type(donation.getType())
                .supporterName(isAnonymous ? null : donation.getSupporterName())
                .message(donation.getMessage())
                .isAccountLinkedDisplay(!isAnonymous && Boolean.TRUE.equals(donation.getIsAccountLinkedDisplay()))
                .amount(formatAmount(donation.getAmountCents()))
                .currency(donation.getCurrency())
                .isAnonymous(isAnonymous)
                .receivedAt(donation.getReceivedAt())
                .build();
    }

    private PendingAccountTransferDonationResponseDTO toPendingResponseDto(DonationVO donation) {
        return PendingAccountTransferDonationResponseDTO.builder()
                .donationId(donation.getDonationId())
                .source(donation.getSource())
                .accountId(donation.getWikisprintAccountId())
                .accountNick(donation.getAccountNick())
                .accountProfileImgUrl(donation.getAccountProfileImgUrl())
                .supporterName(donation.getSupporterName())
                .remitterName(donation.getKofiAccountId())
                .message(donation.getMessage())
                .isAccountLinkedDisplay(Boolean.TRUE.equals(donation.getIsAccountLinkedDisplay()))
                .coffeeCount(calculateCoffeeCount(donation.getAmountCents()))
                .amount(formatAmount(donation.getAmountCents()))
                .currency(donation.getCurrency())
                .isAnonymous(donation.getIsAnonymous())
                .receivedAt(donation.getReceivedAt())
                .build();
    }

    private String generateDonationId() {
        return "DON-" + Generators.timeBasedEpochGenerator().generate();
    }

    private String generateAlertReplayId() {
        return "DAL-" + Generators.timeBasedEpochGenerator().generate();
    }

    private void pruneAlertReplayEvents(LocalDateTime cutoff) {
        while (true) {
            DonationAlertReplayEvent firstEvent = alertReplayEvents.peekFirst();
            if (firstEvent == null || !firstEvent.createdAt().isBefore(cutoff)) {
                return;
            }
            alertReplayEvents.pollFirst();
        }
    }

    private void trimAlertReplayEvents() {
        while (alertReplayEvents.size() > MAX_ALERT_REPLAY_EVENTS) {
            alertReplayEvents.pollFirst();
        }
    }

    private ResolvedSupporter resolveSupporter(AccountVO requester, String nickname, boolean requestedAnonymous) {
        if (requestedAnonymous) {
            return new ResolvedSupporter(null, true);
        }

        if (!isBlank(nickname)) {
            return new ResolvedSupporter(nickname.trim(), false);
        }

        if (requester != null && !isBlank(requester.getNick())) {
            return new ResolvedSupporter(requester.getNick().trim(), false);
        }

        return new ResolvedSupporter(null, true);
    }

    private long calculateAccountTransferAmount(int coffeeCount) {
        return (long) coffeeCount * KRW_PER_COFFEE * 100L;
    }

    private int calculateCoffeeCount(Long amountCents) {
        if (amountCents == null) {
            return 0;
        }
        return (int) (amountCents / (KRW_PER_COFFEE * 100L));
    }

    private int validateCoffeeCount(Integer coffeeCount) {
        if (coffeeCount == null) {
            throw new InvalidDonationRequestException("커피 개수는 필수입니다.");
        }
        if (coffeeCount < 1 || coffeeCount > MAX_COFFEE_COUNT) {
            throw new InvalidDonationRequestException("커피 개수는 1개 이상 100개 이하만 가능합니다.");
        }
        return coffeeCount;
    }

    private String requiredText(String value, String message) {
        if (isBlank(value)) {
            throw new InvalidDonationRequestException(message);
        }
        return value.trim();
    }

    private AccountVO resolveWikiSprintAccount(String email) {
        if (isBlank(email)) {
            return null;
        }

        return accountMapper.selectAccountByEmail(email.trim());
    }

    private boolean resolveAccountLinkedDisplay(AccountVO linkedAccount, String supporterName, boolean isAnonymous) {
        if (isAnonymous || linkedAccount == null) {
            return false;
        }
        if (isBlank(supporterName)) {
            return true;
        }

        return !isBlank(linkedAccount.getNick()) && linkedAccount.getNick().trim().equals(supporterName.trim());
    }

    private boolean isTokenValid(String verificationToken) {
        if (isBlank(webhookToken) || isBlank(verificationToken)) {
            return false;
        }

        byte[] expected = webhookToken.trim().getBytes(StandardCharsets.UTF_8);
        byte[] actual = verificationToken.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private JsonNode parseJson(String rawData) {
        try {
            return OBJECT_MAPPER.readTree(rawData);
        } catch (Exception exception) {
            return null;
        }
    }

    private String readRequiredText(JsonNode rootNode, String fieldName, String message) {
        String value = readText(rootNode, fieldName);
        if (isBlank(value)) {
            throw new InvalidWebhookPayloadException(message);
        }
        return value;
    }

    private String readText(JsonNode rootNode, String fieldName) {
        JsonNode valueNode = rootNode.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }

        String value = valueNode.asText();
        return isBlank(value) ? null : value.trim();
    }

    private boolean readBoolean(JsonNode rootNode, String fieldName, boolean defaultValue) {
        JsonNode valueNode = rootNode.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return defaultValue;
        }
        return valueNode.asBoolean(defaultValue);
    }

    private String validateType(String type) {
        String normalizedType = validateLength(type, MAX_TYPE_LENGTH, "후원 타입");
        if (!normalizedType.matches("[A-Za-z0-9 _-]+")) {
            throw new InvalidWebhookPayloadException("후원 타입 형식이 올바르지 않습니다.");
        }
        return normalizedType;
    }

    private String validateCurrency(String currency) {
        String normalizedCurrency = currency.trim().toUpperCase();
        if (!ALLOWED_CURRENCIES.contains(normalizedCurrency)) {
            throw new InvalidWebhookPayloadException("지원하지 않는 통화입니다.");
        }
        return normalizedCurrency;
    }

    private String validateLength(String value, int maxLength, String fieldLabel) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        if (trimmedValue.length() > maxLength) {
            throw new InvalidDonationRequestException(fieldLabel + " 길이가 너무 깁니다.");
        }
        return trimmedValue;
    }

    private long parseAmountMinorUnits(String rawAmount) {
        try {
            BigDecimal amount = new BigDecimal(rawAmount.trim());
            if (amount.scale() > 2) {
                amount = amount.setScale(2, RoundingMode.HALF_UP);
            }

            BigDecimal amountCents = amount.movePointRight(2);
            long value = amountCents.longValueExact();
            if (value < 0) {
                throw new InvalidWebhookPayloadException("후원 금액은 0 이상이어야 합니다.");
            }
            return value;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new InvalidWebhookPayloadException("후원 금액 형식이 올바르지 않습니다.");
        }
    }

    private String formatAmount(Long amountCents) {
        if (amountCents == null) {
            return null;
        }

        BigDecimal amount = BigDecimal.valueOf(amountCents, 2).stripTrailingZeros();
        return amount.scale() < 0 ? amount.setScale(0).toPlainString() : amount.toPlainString();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum KofiWebhookResult {
        SAVED,
        DUPLICATE,
        SKIPPED_DISABLED
    }

    private record ResolvedSupporter(String supporterName, boolean anonymous) {
    }

    private record DonationAlertReplayEvent(
            String alertId,
            String donationId,
            String adminAccountId,
            LocalDateTime createdAt
    ) {
    }

    public static class InvalidWebhookTokenException extends RuntimeException {
    }

    public static class InvalidWebhookPayloadException extends RuntimeException {
        public InvalidWebhookPayloadException(String message) {
            super(message);
        }
    }

    public static class InvalidDonationRequestException extends RuntimeException {
        public InvalidDonationRequestException(String message) {
            super(message);
        }
    }

    public static class DonationNotFoundException extends RuntimeException {
    }
}
