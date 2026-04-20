package com.wikisprint.server;

import com.wikisprint.server.dto.DonationResponseDTO;
import com.wikisprint.server.mapper.DonationMapper;
import com.wikisprint.server.service.DonationService;
import com.wikisprint.server.vo.DonationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DonationServiceTest {

    @Mock
    private DonationMapper donationMapper;

    private DonationService donationService;

    @BeforeEach
    void setUp() {
        donationService = new DonationService(donationMapper);
        ReflectionTestUtils.setField(donationService, "webhookEnabled", true);
        ReflectionTestUtils.setField(donationService, "webhookToken", "valid-token");
    }

    @Test
    void processKofiWebhook_savesDonationWhenTokenAndPayloadAreValid() {
        when(donationMapper.existsBySourceAndExternalId(eq("kofi"), eq("abc123"))).thenReturn(false);

        donationService.processKofiWebhook(
                "valid-token",
                "{\"message_id\":\"abc123\",\"type\":\"Donation\",\"is_public\":true,\"from_name\":\"Tester\",\"message\":\"hello\",\"amount\":\"5\",\"currency\":\"USD\",\"email\":\"test@test.com\"}"
        );

        ArgumentCaptor<DonationVO> captor = ArgumentCaptor.forClass(DonationVO.class);
        verify(donationMapper).insertDonation(captor.capture());

        DonationVO savedDonation = captor.getValue();
        assertThat(savedDonation.getSource()).isEqualTo("kofi");
        assertThat(savedDonation.getExternalId()).isEqualTo("abc123");
        assertThat(savedDonation.getType()).isEqualTo("Donation");
        assertThat(savedDonation.getSupporterName()).isEqualTo("Tester");
        assertThat(savedDonation.getMessage()).isEqualTo("hello");
        assertThat(savedDonation.getAmount()).isEqualTo("5");
        assertThat(savedDonation.getCurrency()).isEqualTo("USD");
        assertThat(savedDonation.getIsPublic()).isTrue();
        assertThat(savedDonation.getEmail()).isEqualTo("test@test.com");
        assertThat(savedDonation.getPayload()).contains("\"message_id\":\"abc123\"");
    }

    @Test
    void processKofiWebhook_skipsWhenTokenIsInvalid() {
        donationService.processKofiWebhook(
                "wrong-token",
                "{\"message_id\":\"abc123\",\"type\":\"Donation\"}"
        );

        verify(donationMapper, never()).insertDonation(any(DonationVO.class));
    }

    @Test
    void processKofiWebhook_skipsWhenExternalIdIsDuplicated() {
        when(donationMapper.existsBySourceAndExternalId(eq("kofi"), eq("abc123"))).thenReturn(true);

        donationService.processKofiWebhook(
                "valid-token",
                "{\"message_id\":\"abc123\",\"type\":\"Donation\"}"
        );

        verify(donationMapper, never()).insertDonation(any(DonationVO.class));
    }

    @Test
    void processKofiWebhook_savesWhenMessageIsMissing() {
        when(donationMapper.existsBySourceAndExternalId(eq("kofi"), eq("abc123"))).thenReturn(false);

        donationService.processKofiWebhook(
                "valid-token",
                "{\"message_id\":\"abc123\",\"type\":\"Donation\",\"from_name\":\"Tester\",\"amount\":\"5\",\"currency\":\"USD\"}"
        );

        ArgumentCaptor<DonationVO> captor = ArgumentCaptor.forClass(DonationVO.class);
        verify(donationMapper).insertDonation(captor.capture());
        assertThat(captor.getValue().getMessage()).isNull();
    }

    @Test
    void processKofiWebhook_savesPrivateDonation() {
        when(donationMapper.existsBySourceAndExternalId(eq("kofi"), eq("abc123"))).thenReturn(false);

        donationService.processKofiWebhook(
                "valid-token",
                "{\"message_id\":\"abc123\",\"type\":\"Donation\",\"is_public\":false}"
        );

        ArgumentCaptor<DonationVO> captor = ArgumentCaptor.forClass(DonationVO.class);
        verify(donationMapper).insertDonation(captor.capture());
        assertThat(captor.getValue().getIsPublic()).isFalse();
    }

    @Test
    void getLatestDonations_returnsMappedResponseWithoutSensitiveFields() {
        DonationVO donation = new DonationVO();
        donation.setDonationId("DON-1");
        donation.setSource("kofi");
        donation.setType("Donation");
        donation.setSupporterName("Tester");
        donation.setMessage("hello");
        donation.setAmount("5");
        donation.setCurrency("USD");
        donation.setIsPublic(true);
        donation.setEmail("secret@test.com");
        donation.setPayload("{raw}");
        donation.setReceivedAt(LocalDateTime.of(2026, 4, 20, 10, 0));

        when(donationMapper.selectLatestDonations(anyInt())).thenReturn(List.of(donation));

        List<DonationResponseDTO> latestDonations = donationService.getLatestDonations();

        assertThat(latestDonations).hasSize(1);
        assertThat(latestDonations.get(0).getDonationId()).isEqualTo("DON-1");
        assertThat(latestDonations.get(0).getSupporterName()).isEqualTo("Tester");
        assertThat(latestDonations.get(0).getReceivedAt()).isEqualTo(LocalDateTime.of(2026, 4, 20, 10, 0));
    }
}
