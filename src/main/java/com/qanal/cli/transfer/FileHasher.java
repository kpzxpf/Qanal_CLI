package com.qanal.cli.transfer;

import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Computes the xxHash64 of an entire file using a streaming FileChannel read.
 * Matches the DataPlane's {@code StreamingXxHasher} so the sender-provided
 * {@code fileChecksum} is consistent with the DataPlane's final verification.
 */
public final class FileHasher {

    private static final int    BUFFER_SIZE = 8 * 1024 * 1024; // 8 MB
    private static final long   SEED        = 0L;

    private FileHasher() {}

    public static String hexHash(Path file) throws IOException {
        StreamingXXHash64 hasher = XXHashFactory.fastestInstance().newStreamingHash64(SEED);
        byte[]     buf = new byte[BUFFER_SIZE];
        ByteBuffer bb  = ByteBuffer.wrap(buf);

        try (FileChannel fc = FileChannel.open(file, StandardOpenOption.READ)) {
            int n;
            while ((n = fc.read(bb)) > 0) {
                hasher.update(buf, 0, n);
                bb.clear();
            }
        }
        return Long.toHexString(hasher.getValue());
    }
}
