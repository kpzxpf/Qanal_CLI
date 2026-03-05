package com.qanal.cli.transfer;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.*;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * Downloads a file from the egress DataPlane download port via QUIC.
 *
 * <p>Download protocol:
 * <pre>
 *  CLI → DataPlane : transferId (UTF-8, 36 bytes)
 *  DataPlane → CLI : raw file bytes until stream closes
 * </pre>
 */
public class QuicDownloader {

    private static final int TRANSFER_ID_SIZE = 36;
    private static final int BUF_SIZE         = 8 * 1024 * 1024; // 8 MB

    private final String egressHost;
    private final int    egressDownloadPort;

    public QuicDownloader(String egressHost, int egressDownloadPort) {
        this.egressHost        = egressHost;
        this.egressDownloadPort = egressDownloadPort;
    }

    /**
     * Downloads the transfer identified by {@code transferId} and writes it to {@code destFile}.
     *
     * @param transferId  UUID string (36 chars)
     * @param destFile    destination path (will be created/overwritten)
     * @param onProgress  called periodically with total bytes received so far
     * @return total bytes downloaded
     */
    public long download(String transferId, Path destFile, LongConsumer onProgress) throws Exception {
        Files.createDirectories(destFile.getParent() == null ? Path.of(".") : destFile.getParent());

        NioEventLoopGroup group = new NioEventLoopGroup(1);
        AtomicLong totalReceived = new AtomicLong(0);

        try {
            QuicSslContext sslCtx = QuicSslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .applicationProtocols("qanal-download")
                    .build();

            ChannelHandler codec = new QuicClientCodecBuilder()
                    .sslContext(sslCtx)
                    .maxIdleTimeout(120, TimeUnit.SECONDS)
                    .initialMaxData(100 * 1024 * 1024L)
                    .initialMaxStreamDataBidirectionalLocal(16 * 1024 * 1024L)
                    .build();

            Channel udpChannel = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(0).sync().channel();

            try {
                var done = new CountDownLatch(1);

                QuicChannel quicChannel = QuicChannel.newBootstrap(udpChannel)
                        .streamHandler(new ChannelInboundHandlerAdapter())
                        .remoteAddress(new InetSocketAddress(egressHost, egressDownloadPort))
                        .connect().sync().getNow();

                try (FileChannel fc = FileChannel.open(destFile,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                    QuicStreamChannel stream = quicChannel.createStream(
                            QuicStreamType.BIDIRECTIONAL,
                            new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                    ByteBuf buf = (ByteBuf) msg;
                                    try {
                                        int n = buf.readableBytes();
                                        ByteBuffer nio = buf.nioBuffer();
                                        while (nio.hasRemaining()) fc.write(nio);
                                        long total = totalReceived.addAndGet(n);
                                        onProgress.accept(total);
                                    } finally {
                                        buf.release();
                                    }
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    done.countDown();
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    done.countDown();
                                }
                            }
                    ).sync().getNow();

                    // Send transferId request (padded to 36 bytes)
                    byte[] idBytes  = Arrays.copyOf(
                            transferId.getBytes(StandardCharsets.UTF_8), TRANSFER_ID_SIZE);
                    stream.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(idBytes)).sync();

                    // Wait for the server to finish streaming the file
                    if (!done.await(3600, TimeUnit.SECONDS)) {
                        throw new RuntimeException("Download timed out for transfer " + transferId);
                    }
                }

                quicChannel.close().sync();
            } finally {
                udpChannel.close().sync();
            }
        } finally {
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }

        return totalReceived.get();
    }
}
