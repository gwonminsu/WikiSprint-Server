package com.wikisprint.server;

import com.wikisprint.server.dto.DonationResponseDTO;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.mapper.DonationMapper;
import com.wikisprint.server.service.DonationService;
import com.wikisprint.server.service.DonationService.InvalidWebhookPayloadException;
import com.wikisprint.server.service.DonationService.InvalidWebhookTokenException;
import com.wikisprint.server.vo.AccountVO;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock
    private AccountMapper accountMapper;

    private DonationService donationService;

    @BeforeEach
    void setUp() {
        donationService = new DonationService(donationMapper, accountMapper);
        ReflectionTestUtils.setField(donationService, "webhookEnabled", true);
        ReflectionTestUtils.setField(donationService, "webhookToken", "valid-token");
    }

    @Test
    void processKofiWebhook_savesDonationWhenTokenAndPayloadAreValid() {
        when(donationMapper.existsByKofiMessageId("abc123")).thenReturn(false);

        AccountVO account = new AccountVO();
        account.setUuid("ACC-1");
        when(accountMapper.selectAccountByEmail("test@test.com")).thenReturn(account);

        donationService.processKofiWebhook(
                "valid-token",
                "{\"account_id\":\"kofi-user-1\",\"message_id\":\"abc123\",\"type\":\"Donation\",\"is_public\":true,\"from_name\":\"Tester\",\"message\":\"hello\",\"amount\":\"5\",\"currency\":\"USD\",\"email\":\"test@test.com\"}"
        );

        ArgumentCaptor<DonationVO> captor = ArgumentCaptor.forClass(DonationVO.class);
        verify(donationMapper).insertDonation(captor.capture());

        DonationVO savedDonation = captor.getValue();
        assertThat(savedDonation.getSource()).isEqualTo("kofi");
        assertThat(savedDonation.getKofiAccountId()).isEqualTo("kofi-user-1");
        assertThat(savedDonation.getWikisprintAccountId()).isEqualTo("ACC-1");
        assertThat(savedDonation.getKofiMessageId()).isEqualTo("abc123");
        assertThat(savedDonation.getType()).isEqualTo("Donation");
        assertThat(savedDonation.getSupporterName()).isEqualTo("Tester");
        assertThat(savedDonation.getMessage()).isEqualTo("hello");
        assertThat(savedDonation.getAmountCents()).isEqualTo(500L);
        assertThat(savedDonation.getCurrency()).isEqualTo("USD");
        assertThat(savedDonation.getIsAnonymous()).isFalse();
    }

    @Test
    void processKofiWebhook_rejectsWhenTokenIsInvalid() {
        assertThatThrownBy(() -> donationService.processKofiWebhook(
                "wrong-token",
                "{\"message_id\":\"abc123\",\"amount\":\"5\",\"currency\":\"USD\"}"
        )).isInstanceOf(InvalidWebhookTokenException.class);

        verify(donationMapper, never()).insertDonation(any(DonationVO.class));
    }

    @Test
    void processKofiWebhook_rejectsWhenMessageIdIsMissing() {
        assertThatThrownBy(() -> donationService.processKofiWebhook(
                "valid-token",
                "{\"type\":\"Donation\",\"amount\":\"5\",\"currency\":\"USD\"}"
        )).isInstanceOf(InvalidWebhookPayloadException.class);

        verify(donationMapper, never()).insertDonation(any(DonationVO.class));
    }

    @Test
    void processKofiWebhook_deduplicatesByMessageId() {
        when(donationMapper.existsByKofiMessageId("abc123")).thenReturn(true);

        DonationService.KofiWebhookResult result = donationService.processKofiWebhook(
                "valid-token",
                "{\"message_id\":\"abc123\",\"amount\":\"5\",\"currency\":\"USD\"}"
        );

        assertThat(result).isEqualTo(DonationService.KofiWebhookResult.DUPLICATE);
        verify(donationMapper, never()).insertDonation(any(DonationVO.class));
    }

    @Test
    void processKofiWebhook_savesAnonymousDonation() {
        when(donationMapper.existsByKofiMessageId("abc123")).thenReturn(false);

        donationService.processKofiWebhook(
                "valid-token",
                "{\"message_id\":\"abc123\",\"type\":\"Donation\",\"is_public\":false,\"amount\":\"5\",\"currency\":\"USD\"}"
        );

        ArgumentCaptor<DonationVO> captor = ArgumentCaptor.forClass(DonationVO.class);
        verify(donationMapper).insertDonation(captor.capture());
        assertThat(captor.getValue().getIsAnonymous()).isTrue();
    }

    @Test
    void getLatestDonations_masksAnonymousIdentityButKeepsMessage() {
        DonationVO donation = new DonationVO();
        donation.setDonationId("DON-1");
        donation.setSource("kofi");
        donation.setWikisprintAccountId("ACC-1");
        donation.setAccountNick("TesterNick");
        donation.setAccountProfileImgUrl("https://example.com/profile.png");
        donation.setType("Donation");
        donation.setSupporterName("Tester");
        donation.setMessage("hello");
        donation.setAmountCents(500L);
        donation.setCurrency("USD");
        donation.setIsAnonymous(true);
        donation.setReceivedAt(LocalDateTime.of(2026, 4, 20, 10, 0));

        when(donationMapper.selectLatestDonations(anyInt())).thenReturn(List.of(donation));

        List<DonationResponseDTO> latestDonations = donationService.getLatestDonations();

        assertThat(latestDonations).hasSize(1);
        assertThat(latestDonations.get(0).getDonationId()).isEqualTo("DON-1");
        assertThat(latestDonations.get(0).getAccountId()).isEqualTo("ACC-1");
        assertThat(latestDonations.get(0).getSupporterName()).isNull();
        assertThat(latestDonations.get(0).getMessage()).isEqualTo("hello");
        assertThat(latestDonations.get(0).getAccountProfileImgUrl()).isNull();
        assertThat(latestDonations.get(0).getAmount()).isEqualTo("5");
    }

    @Test
    void getRecentAlertDonations_returnsDonationsAfterTenMinuteCutoff() {
        DonationVO donation = new DonationVO();
        donation.setDonationId("DON-RECENT");
        donation.setSource("kofi");
        donation.setType("Donation");
        donation.setSupporterName("Recent");
        donation.setMessage("recent hello");
        donation.setAmountCents(500L);
        donation.setCurrency("USD");
        donation.setIsAnonymous(false);
        donation.setReceivedAt(LocalDateTime.of(2026, 4, 20, 10, 0));

        when(donationMapper.selectRecentDonations(any(LocalDateTime.class))).thenReturn(List.of(donation));

        List<DonationResponseDTO> recentDonations = donationService.getRecentAlertDonations();

        assertThat(recentDonations).hasSize(1);
        assertThat(recentDonations.get(0).getDonationId()).isEqualTo("DON-RECENT");
        assertThat(recentDonations.get(0).getMessage()).isEqualTo("recent hello");

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(donationMapper).selectRecentDonations(captor.capture());
        assertThat(captor.getValue()).isBefore(LocalDateTime.now());
        assertThat(captor.getValue()).isAfter(LocalDateTime.now().minusMinutes(11));
    }

    @Test
    void getDonationById_returnsNullWhenNoDonationExists() {
        when(donationMapper.selectDonationById(eq("DON-404"))).thenReturn(null);

        DonationResponseDTO donation = donationService.getDonationById("DON-404");

        assertThat(donation).isNull();
    }
}
