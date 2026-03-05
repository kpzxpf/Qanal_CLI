package com.qanal.cli.transfer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Encodes the 68-byte big-endian chunk header that the DataPlane's
 * {@code ChunkReceiver} expects on every QUIC stream.
 *
 * <pre>
 *  Byte  0-35 : transferId  (UTF-8, 36 bytes, fixed UUID string)
 *  Byte 36-39 : chunkIndex  (int32)
 *  Byte 40-47 : offsetBytes (int64)
 *  Byte 48-55 : sizeBytes   (int64)
 *  Byte 56-63 : totalFileSize (int64)
 *  Byte 64-67 : totalChunks  (int32)
 * </pre>
 */
public final class ChunkHeaderEncoder {

    public static final int HEADER_SIZE = 68;

    private ChunkHeaderEncoder() {}

    public static byte[] encode(String transferId, int chunkIndex,
                                long offsetBytes, long sizeBytes,
                                long totalFileSize, int totalChunks) {
        byte[] idBytes = transferId.getBytes(StandardCharsets.UTF_8);
        if (idBytes.length != 36) {
            throw new IllegalArgumentException("transferId must be 36 bytes, got " + idBytes.length);
        }

        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        buf.put(idBytes);               // 0-35
        buf.putInt(chunkIndex);         // 36-39
        buf.putLong(offsetBytes);       // 40-47
        buf.putLong(sizeBytes);         // 48-55
        buf.putLong(totalFileSize);     // 56-63
        buf.putInt(totalChunks);        // 64-67
        return buf.array();
    }
}
