package com.wikisprint.server.dto;

import lombok.Data;

import java.util.List;

@Data
public class RegisterReqDTO {
    private String credential;
    private List<ConsentItemDTO> consents;
}