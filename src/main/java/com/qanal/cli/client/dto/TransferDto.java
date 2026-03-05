package com.qanal.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferDto(
        String         id,
        String         status,
        String         fileName,
        long           fileSize,
        int            totalChunks,
        int            completedChunks,
        int            progressPercent,
        long           bytesTransferred,
        Double         avgThroughputBps,
        String         relayHost,
        int            relayQuicPort,
        String         egressRelayHost,
        int            egressDownloadPort,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        OffsetDateTime completedAt,
        List<ChunkDto> chunks
) {
    /** Returns the effective download host and port.
     *  If egress is a separate node, use egressRelayHost:egressDownloadPort.
     *  Otherwise fall back to relayHost and the conventional download port (relayQuicPort + 1). */
    public String downloadHost() {
        return egressRelayHost != null && !egressRelayHost.isEmpty() ? egressRelayHost : relayHost;
    }

    public int downloadPort() {
        return egressDownloadPort > 0 ? egressDownloadPort : relayQuicPort + 1;
    }
}
