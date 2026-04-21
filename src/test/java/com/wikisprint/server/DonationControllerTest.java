package com.wikisprint.server;

import com.wikisprint.server.controller.DonationController;
import com.wikisprint.server.controller.DonationWebhookController;
import com.wikisprint.server.dto.DonationResponseDTO;
import com.wikisprint.server.service.DonationService;
import com.wikisprint.server.service.DonationService.InvalidWebhookPayloadException;
import com.wikisprint.server.service.DonationService.InvalidWebhookTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DonationControllerTest {

    @Mock
    private DonationService donationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new DonationWebhookController(donationService),
                new DonationController(donationService)
        ).build();
    }

    @Test
    void receiveKofiWebhook_returnsOkForValidRequest() throws Exception {
        when(donationService.processKofiWebhook(
                eq("valid-token"),
                eq("{\"message_id\":\"abc123\",\"type\":\"Donation\",\"amount\":\"5\",\"currency\":\"USD\"}")
        )).thenReturn(DonationService.KofiWebhookResult.SAVED);

        mockMvc.perform(post("/webhook/kofi")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("verification_token", "valid-token")
                        .param("data", "{\"message_id\":\"abc123\",\"type\":\"Donation\",\"amount\":\"5\",\"currency\":\"USD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("웹훅을 저장했습니다."));

        verify(donationService).processKofiWebhook(
                eq("valid-token"),
                eq("{\"message_id\":\"abc123\",\"type\":\"Donation\",\"amount\":\"5\",\"currency\":\"USD\"}")
        );
    }

    @Test
    void receiveKofiWebhook_returnsUnauthorizedForInvalidToken() throws Exception {
        when(donationService.processKofiWebhook(eq("wrong-token"), eq("{\"message_id\":\"abc123\"}")))
                .thenThrow(new InvalidWebhookTokenException());

        mockMvc.perform(post("/webhook/kofi")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("verification_token", "wrong-token")
                        .param("data", "{\"message_id\":\"abc123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("verification_token이 올바르지 않습니다."));
    }

    @Test
    void receiveKofiWebhook_returnsBadRequestForInvalidPayload() throws Exception {
        when(donationService.processKofiWebhook(eq("valid-token"), eq("{\"type\":\"Donation\"}")))
                .thenThrow(new InvalidWebhookPayloadException("message_id는 필수입니다."));

        mockMvc.perform(post("/webhook/kofi")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("verification_token", "valid-token")
                        .param("data", "{\"type\":\"Donation\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("message_id는 필수입니다."));
    }

    @Test
    void getLatestDonations_returnsLatestDonationList() throws Exception {
        when(donationService.getLatestDonations()).thenReturn(List.of(
                DonationResponseDTO.builder()
                        .donationId("DON-1")
                        .source("kofi")
                        .accountId("ACC-1")
                        .type("Donation")
                        .supporterName("Tester")
                        .message("hello")
                        .amount("5")
                        .currency("USD")
                        .isAnonymous(false)
                        .receivedAt(LocalDateTime.of(2026, 4, 20, 10, 0))
                        .build()
        ));

        mockMvc.perform(post("/donations/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].donationId").value("DON-1"))
                .andExpect(jsonPath("$.data[0].supporterName").value("Tester"))
                .andExpect(jsonPath("$.data[0].message").value("hello"));
    }

    @Test
    void getAllDonations_returnsAllDonationList() throws Exception {
        when(donationService.getAllDonations()).thenReturn(List.of(
                DonationResponseDTO.builder()
                        .donationId("DON-2")
                        .source("kofi")
                        .type("Donation")
                        .supporterName(null)
                        .message(null)
                        .amount("2")
                        .currency("USD")
                        .isAnonymous(true)
                        .receivedAt(LocalDateTime.of(2026, 4, 21, 9, 0))
                        .build()
        ));

        mockMvc.perform(post("/donations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].donationId").value("DON-2"))
                .andExpect(jsonPath("$.data[0].isAnonymous").value(true));
    }
}
