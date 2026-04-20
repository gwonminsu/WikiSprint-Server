package com.wikisprint.server;

import com.wikisprint.server.controller.DonationController;
import com.wikisprint.server.controller.DonationWebhookController;
import com.wikisprint.server.dto.DonationResponseDTO;
import com.wikisprint.server.service.DonationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void receiveKofiWebhook_returnsOkForFormUrlEncodedRequest() throws Exception {
        mockMvc.perform(post("/webhook/kofi")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("verification_token", "valid-token")
                        .param("data", "{\"message_id\":\"abc123\",\"type\":\"Donation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("웹훅 처리 완료"));

        verify(donationService).processKofiWebhook(
                eq("valid-token"),
                eq("{\"message_id\":\"abc123\",\"type\":\"Donation\"}")
        );
    }

    @Test
    void getLatestDonations_returnsLatestDonationList() throws Exception {
        when(donationService.getLatestDonations()).thenReturn(List.of(
                DonationResponseDTO.builder()
                        .donationId("DON-1")
                        .source("kofi")
                        .type("Donation")
                        .supporterName("Tester")
                        .message("hello")
                        .amount("5")
                        .currency("USD")
                        .isPublic(true)
                        .receivedAt(LocalDateTime.of(2026, 4, 20, 10, 0))
                        .build()
        ));

        mockMvc.perform(get("/donations/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].donationId").value("DON-1"))
                .andExpect(jsonPath("$.data[0].supporterName").value("Tester"))
                .andExpect(jsonPath("$.data[0].message").value("hello"));
    }
}
