package com.wikisprint.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PageRequestDTO {

    private int page = 0;

    private int size = 10;

    @JsonProperty("account_uuid")
    private String accountUuid;

}
