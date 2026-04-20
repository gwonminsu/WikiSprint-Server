package com.wikisprint.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.uuid.Generators;
import com.wikisprint.server.dto.DonationResponseDTO;
import com.wikisprint.server.mapper.DonationMapper;
import com.wikisprint.server.vo.DonationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Ko-fi 웹훅 저장과 후원 조회를 담당한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class DonationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE_KOFI = "kofi";
    private static final int LATEST_DONATION_LIMIT = 20;

    private final DonationMapper donationMapper;

    @Value("${kofi.webhook-enabled:false}")
    private boolean webhookEnabled;

    @Value("${kofi.webhook-token:}")
    private String webhookToken;

    @Transactional
    public void processKofiWebhook(String verificationToken, String rawData) {
        if (!webhookEnabled) {
            log.info("Ko-fi webhook skipped: disabled");
            return;
        }

        if (isBlank(webhookToken) || !webhookToken.equals(verificationToken)) {
            log.warn("Ko-fi webhook skipped: invalid token");
            return;
        }

        if (isBlank(rawData)) {
            log.warn("Ko-fi webhook skipped: empty payload");
            return;
        }

        JsonNode rootNode = parseJson(rawData);
        if (rootNode == null || rootNode.isNull()) {
            log.warn("Ko-fi webhook skipped: invalid data json");
            return;
        }

        String externalId = firstNonBlank(
                readText(rootNode, "message_id"),
                readText(rootNode, "transaction_id")
        );

        if (!isBlank(externalId) && donationMapper.existsBySourceAndExternalId(SOURCE_KOFI, externalId)) {
            log.info("Ko-fi webhook skipped: duplicate externalId={}", externalId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        DonationVO donation = new DonationVO();
        donation.setDonationId("DON-" + Generators.timeBasedEpochGenerator().generate());
        donation.setSource(SOURCE_KOFI);
        donation.setExternalId(externalId);
        donation.setType(defaultIfBlank(readText(rootNode, "type"), "unknown"));
        donation.setSupporterName(readText(rootNode, "from_name"));
        donation.setMessage(readText(rootNode, "message"));
        donation.setAmount(readText(rootNode, "amount"));
        donation.setCurrency(readText(rootNode, "currency"));
        donation.setIsPublic(readBoolean(rootNode, "is_public", true));
        donation.setEmail(readText(rootNode, "email"));
        donation.setPayload(rawData);
        donation.setReceivedAt(now);
        donation.setCreatedAt(now);

        donationMapper.insertDonation(donation);
        log.info("Ko-fi webhook saved: donationId={}, externalId={}", donation.getDonationId(), externalId);
    }

    @Transactional(readOnly = true)
    public List<DonationResponseDTO> getLatestDonations() {
        return donationMapper.selectLatestDonations(LATEST_DONATION_LIMIT)
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

    DonationResponseDTO toResponseDto(DonationVO donation) {
        return DonationResponseDTO.builder()
                .donationId(donation.getDonationId())
                .source(donation.getSource())
                .type(donation.getType())
                .supporterName(donation.getSupporterName())
                .message(donation.getMessage())
                .amount(donation.getAmount())
                .currency(donation.getCurrency())
                .isPublic(donation.getIsPublic())
                .receivedAt(donation.getReceivedAt())
                .build();
    }

    private JsonNode parseJson(String rawData) {
        try {
            return OBJECT_MAPPER.readTree(rawData);
        } catch (Exception e) {
            return null;
        }
    }

    private String readText(JsonNode rootNode, String fieldName) {
        JsonNode valueNode = rootNode.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }

        String value = valueNode.asText();
        return isBlank(value) ? null : value.trim();
    }

    private Boolean readBoolean(JsonNode rootNode, String fieldName, boolean defaultValue) {
        JsonNode valueNode = rootNode.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return defaultValue;
        }
        return valueNode.asBoolean(defaultValue);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private String firstNonBlank(String firstValue, String secondValue) {
        if (!isBlank(firstValue)) {
            return firstValue;
        }
        if (!isBlank(secondValue)) {
            return secondValue;
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
