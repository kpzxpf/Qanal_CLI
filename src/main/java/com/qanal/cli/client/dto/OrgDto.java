package com.qanal.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrgDto(
        String         id,
        String         name,
        String         plan,
        long           bytesUsedThisMonth,
        OffsetDateTime createdAt
) {}
