package com.qanal.cli.transfer;

import com.qanal.cli.client.dto.ChunkDto;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.*;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sends file chunks to the DataPlane over QUIC.
 *
 * <p>One {@link QuicChannel} is opened to the relay; each chunk gets its own
 * bidirectional QUIC stream. Chunks are sent in parallel up to
 * {@code parallelStreams} concurrent streams.
 */
public class QuicSender implements AutoCloseable {

    private static final int  READ_BUFFER  = 2 * 1024 * 1024; // 2 MB
    private static final int  CONNECT_TIMEOUT_SEC = 10;

    private final NioEventLoopGroup group;
    private final QuicChannel       quicChannel;

    // Progress tracking
    private final AtomicLong bytesSent    = new AtomicLong(0);
    private final AtomicReference<Throwable> firstError = new AtomicReference<>();

    public QuicSender(String relayHost, int relayPort) throws Exception {
        QuicSslContext sslCtx = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols("qanal")
                .build();

        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(sslCtx)
                .maxIdleTimeout(60, TimeUnit.SECONDS)
                .initialMaxData(100L * 1024 * 1024)
                .initialMaxStreamDataBidirectionalLocal(16L * 1024 * 1024)
                .build();

        group = new NioEventLoopGroup(1);

        Channel udpChannel = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(0)
                .sync()
                .channel();

        quicChannel = QuicChannel.newBootstrap(udpChannel)
                .streamHandler(new ChannelInboundHandlerAdapter())  // no server-initiated streams
                .remoteAddress(new InetSocketAddress(relayHost, relayPort))
                .connectTimeout(CONNECT_TIMEOUT_SEC * 1000L)
                .connect()
                .get(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    /**
     * Sends all chunks in parallel (up to {@code parallelStreams} at a time).
     *
     * @param file          local file to read
     * @param transferId    UUID assigned by Control Plane
     * @param chunks        chunk plan from Control Plane
     * @param parallelStreams max concurrent QUIC streams
     * @throws Exception if any chunk fails
     */
    public void sendAll(Path file, String transferId, List<ChunkDto> chunks, int parallelStreams)
            throws Exception {

        int totalChunks   = chunks.size();
        long totalFileSize = chunks.stream().mapToLong(ChunkDto::sizeBytes).sum();

        var semaphore = new Semaphore(parallelStreams);
        var latch     = new CountDownLatch(totalChunks);

        try (FileChannel fc = FileChannel.open(file, StandardOpenOption.READ)) {
            for (ChunkDto chunk : chunks) {
                if (firstError.get() != null) break;

                semaphore.acquire();
                Thread.ofVirtual().name("chunk-" + chunk.chunkIndex()).start(() -> {
                    try {
                        sendChunk(fc, transferId, chunk, totalChunks, totalFileSize);
                    } catch (Exception e) {
                        firstError.compareAndSet(null, e);
                    } finally {
                        semaphore.release();
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        if (firstError.get() != null) {
            throw new RuntimeException("Transfer failed: " + firstError.get().getMessage(),
                    firstError.get());
        }
    }

    public long getBytesSent() {
        return bytesSent.get();
    }

    @Override
    public void close() {
        try {
            quicChannel.close().sync();
        } catch (Exception ignored) {}
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void sendChunk(FileChannel fc, String transferId, ChunkDto chunk,
                           int totalChunks, long totalFileSize) throws Exception {
        byte[] header = ChunkHeaderEncoder.encode(
                transferId,
                chunk.chunkIndex(),
                chunk.offsetBytes(),
                chunk.sizeBytes(),
                totalFileSize,
                totalChunks
        );

        CompletableFuture<Void> done = new CompletableFuture<>();

        QuicStreamChannel stream = quicChannel.createStream(
                QuicStreamType.BIDIRECTIONAL,
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) {
                        done.complete(null);
                    }
                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        done.completeExceptionally(cause);
                        ctx.close();
                    }
                }
        ).sync().getNow();

        // Write header
        stream.write(Unpooled.wrappedBuffer(header));

        // Write chunk data in READ_BUFFER-sized pieces
        ByteBuffer buf   = ByteBuffer.allocate(READ_BUFFER);
        long       pos   = chunk.offsetBytes();
        long       end   = chunk.offsetBytes() + chunk.sizeBytes();

        while (pos < end) {
            buf.clear();
            int limit = (int) Math.min(READ_BUFFER, end - pos);
            buf.limit(limit);

            int n = fc.read(buf, pos);
            if (n <= 0) break;

            buf.flip();
            byte[] slice = new byte[buf.remaining()];
            buf.get(slice);

            stream.write(Unpooled.wrappedBuffer(slice));
            bytesSent.addAndGet(n);
            pos += n;
        }

        // Signal end of stream — DataPlane uses channelInactive to finalize
        stream.shutdownOutput().sync();
        stream.writeAndFlush(Unpooled.EMPTY_BUFFER).sync();

        // Wait for the stream to close (DataPlane closes it after processing)
        done.get(60, TimeUnit.SECONDS);
    }
}
