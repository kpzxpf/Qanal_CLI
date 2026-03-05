package com.qanal.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChunkDto(
        int  chunkIndex,
        long offsetBytes,
        long sizeBytes
) {}
