package dev.by1337.particle.hook;

import dev.by1337.particle.ParticleType;
import dev.by1337.particle.netty.handler.ParticleEncoder;
import dev.by1337.particle.particle.*;
import dev.by1337.particle.particle.options.*;
import dev.by1337.particle.via.Mappings;
import dev.by1337.particle.via.ParticleWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.*;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ThemedDustTest {
    private static String noVia;
    @BeforeClass public static void disableVia() {
        noVia = System.getProperty("particle.render.no_via");
        System.setProperty("particle.render.no_via", "true");
    }
    @AfterClass public static void restoreVia() {
        if (noVia == null) System.clearProperty("particle.render.no_via");
        else System.setProperty("particle.render.no_via", noVia);
    }
    @After public void resetHook() { ThemeColorContext.setProvider(null); }

    @Test
    public void sameWirePacketsAsNativeDustAcrossAllProtocols() {
        ParticleData themed = ParticleData.of(ParticleType.THEME_DUST, new ThemedDustParticleOptions(2.5f));
        ParticleData nativeDust = ParticleData.of(ParticleType.DUST, new DustParticleOptions(0x123456, 2.5f));
        try (var ignored = ThemeColorContext.push(0x123456)) {
            for (int protocol = Mappings.MIN_VERSION; protocol <= Mappings.MAX_VERSION; protocol++) {
                assertEquals(Mappings.getParticleId(ParticleType.DUST, protocol), Mappings.getParticleId(ParticleType.THEME_DUST, protocol));
                assertArrayEquals(packet(nativeDust, protocol), packet(themed, protocol));
            }
        }
    }

    @Test
    public void defaultsToWhiteAndPreservesSizeFromOrdinaryDustOptions() {
        ParticleData themed = ParticleData.of(ParticleType.THEME_DUST, new DustParticleOptions(0xFF0000, 3f));
        assertEquals(new ThemedDustParticleOptions(3f), themed.data);
        assertArrayEquals(packet(ParticleData.of(ParticleType.DUST, new DustParticleOptions(0xFFFFFF, 3f)), 777), packet(themed, 777));
        assertEquals(ThemedDustParticleOptions.DEFAULT, ParticleData.of(ParticleType.THEME_DUST).data);
        DustParticleOptions unchanged = new DustParticleOptions(0xFF0000, 2f);
        assertSame(unchanged, ParticleData.of(ParticleType.DUST, unchanged).data);
    }

    @Test
    public void samplesViewerColorOnceForEachSourceAndDoesNotFreezePrecomputedSource() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        AtomicInteger color = new AtomicInteger(0x112233), calls = new AtomicInteger();
        ThemeColorContext.setProvider(viewer -> { calls.incrementAndGet(); return viewer.equals(first) ? color.get() : 0xABCDEF; });
        ParticleData themed = ParticleData.of(ParticleType.THEME_DUST);
        ParticleSource source = new ParticleSource() {
            @Override public void doWrite(PacketBuilder writer, double x, double y, double z) {
                for (int i = 0; i < 8; i++) writer.append(themed, 0, 0, 0);
            }
        }.compute();
        EmbeddedChannel one = channel(first), two = channel(second);
        try {
            assertFrames(one, source, 0x112233, 8);
            color.set(0x445566);
            assertFrames(one, source, 0x445566, 8);
            assertFrames(two, source, 0xABCDEF, 8);
            assertEquals(3, calls.get());
            assertEquals(ThemeColorContext.WHITE, ThemeColorContext.current());
        } finally { one.finishAndReleaseAll(); two.finishAndReleaseAll(); }
    }

    @Test
    public void restoresOuterContextEvenWhenSourceThrows() {
        ThemeColorContext.setProvider(viewer -> 0xABCDEF);
        EmbeddedChannel channel = channel(UUID.randomUUID());
        try (var ignored = ThemeColorContext.push(0x112233)) {
            channel.writeOutbound(new ParticleSource() {
                @Override public void doWrite(PacketBuilder writer, double x, double y, double z) {
                    assertEquals(0xABCDEF, ThemeColorContext.current());
                    throw new IllegalStateException("test source failure");
                }
            });
            assertEquals(0x112233, ThemeColorContext.current());
        } finally { channel.finishAndReleaseAll(); }
        assertEquals(ThemeColorContext.WHITE, ThemeColorContext.current());
    }

    @Test
    public void unavailableInvalidAndFailedHooksReturnWhite() {
        UUID viewer = UUID.randomUUID();
        assertEquals(ThemeColorContext.WHITE, ThemeColorContext.resolve(viewer));
        ThemeColorContext.setProvider(id -> -1);
        assertEquals(ThemeColorContext.WHITE, ThemeColorContext.resolve(viewer));
        ThemeColorContext.setProvider(id -> { throw new IllegalStateException("unavailable"); });
        assertEquals(ThemeColorContext.WHITE, ThemeColorContext.resolve(viewer));
        ThemeColorContext.setProvider(id -> 0x123456);
        assertEquals(ThemeColorContext.WHITE, ThemeColorContext.resolve(null));
    }

    @Test
    public void threadLocalContextsStayIndependent() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<Integer> first = pool.submit(() -> scoped(0x112233, barrier));
            Future<Integer> second = pool.submit(() -> scoped(0xAABBCC, barrier));
            assertEquals(Integer.valueOf(0x112233), first.get());
            assertEquals(Integer.valueOf(0xAABBCC), second.get());
        } finally { pool.shutdownNow(); }
    }

    private static int scoped(int color, CyclicBarrier barrier) throws Exception {
        try (var ignored = ThemeColorContext.push(color)) {
            barrier.await(5, TimeUnit.SECONDS);
            return ThemeColorContext.current();
        } finally { assertEquals(ThemeColorContext.WHITE, ThemeColorContext.current()); }
    }
    private static EmbeddedChannel channel(UUID viewer) {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new ParticleEncoder(channel, viewer, 777));
        return channel;
    }
    private static void assertFrames(EmbeddedChannel channel, ParticleSource source, int color, int count) {
        channel.writeOutbound(source);
        ByteBuf frames = channel.readOutbound();
        try {
            byte[] expected = packet(ParticleData.of(ParticleType.DUST, new DustParticleOptions(color, 1f)), 777);
            for (int i = 0; i < count; i++) {
                int length = readVarInt(frames);
                assertArrayEquals(expected, ByteBufUtil.getBytes(frames.readSlice(length)));
            }
            assertFalse(frames.isReadable());
        } finally { frames.release(); }
    }
    private static byte[] packet(ParticleData particle, int protocol) {
        ByteBuf bytes = Unpooled.buffer();
        try {
            assertEquals(protocol, ParticleWriter.write(protocol, bytes, particle, 0, 0, 0, 0, 0, 0));
            return ByteBufUtil.getBytes(bytes);
        } finally { bytes.release(); }
    }
    private static int readVarInt(ByteBuf buffer) {
        int value = 0, shift = 0;
        byte next;
        do { next = buffer.readByte(); value |= (next & 127) << shift; shift += 7; } while ((next & 128) != 0);
        return value;
    }
}
