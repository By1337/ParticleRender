package dev.by1337.particle.via;

import dev.by1337.particle.ItemType;
import dev.by1337.particle.ParticleType;
import dev.by1337.particle.netty.handler.ParticleEncoder;
import dev.by1337.particle.particle.ParticleData;
import dev.by1337.particle.particle.ParticleOption;
import dev.by1337.particle.particle.ParticleOptionType;
import dev.by1337.particle.particle.options.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class ParticleProtocolTest {
    private static String previousNoVia;

    @BeforeClass
    public static void disableViaForStandaloneEncoder() {
        // ViaVersion is not initialized in these standalone Netty tests.
        previousNoVia = System.getProperty("particle.render.no_via");
        System.setProperty("particle.render.no_via", "true");
    }

    @AfterClass
    public static void restoreViaProperty() {
        if (previousNoVia == null) System.clearProperty("particle.render.no_via");
        else System.setProperty("particle.render.no_via", previousNoVia);
    }

    // Fixtures follow the 26.3 ClientboundLevelParticlesPacket STREAM_CODEC.
    private static final String FIELDS_777 =
            "01003ff4000000000000c004000000000000400e000000000000"
                    + "3e0000003e8000003ec000003f0000003f0000003f000000ac0200";

    @Test
    public void protocol777SimpleParticle() {
        assertPacket(ParticleType.FLAME, null, "3027" + FIELDS_777);
    }

    @Test
    public void protocol777OptionsPrecedeFlags() {
        assertPacket(ParticleType.DUST, new DustParticleOptions(0x123456, 1.5f),
                "3015001234563fc00000" + FIELDS_777);
    }

    @Test
    public void protocol777ItemTemplate() {
        ByteBuf out = Unpooled.buffer();
        try {
            assertEquals(777, write(777, out, sample(ParticleType.ITEM,
                    new ItemParticleOption(ItemType.BARRIER))));
            assertEquals(48, readVarInt(out));
            assertEquals(57, readVarInt(out));
            assertEquals(ItemType.BARRIER.getProtocolId(777), readVarInt(out));
            assertEquals(1, readVarInt(out));
            assertEquals(0, readVarInt(out));
            assertEquals(0, readVarInt(out));
            assertArrayEquals(hex(FIELDS_777), bytes(out));
        } finally {
            out.release();
        }
    }

    @Test
    public void protocol777RejectsEmptyItemBeforeWriting() {
        ByteBuf out = Unpooled.buffer();
        try {
            out.writeByte(42);
            assertEquals(-1, write(777, out, sample(ParticleType.ITEM,
                    new ItemParticleOption(ItemType.AIR))));
            assertEquals(1, out.writerIndex());
            assertTrue(new ItemParticleOption(ItemType.AIR).writable(776));
        } finally {
            out.release();
        }
    }

    @Test
    public void protocol777OptionCodecs() {
        assertOption(new DustColorTransitionOptions(0x123456, 0x654321, 1.5f),
                "00123456006543213fc00000");
        assertOption(new ColorParticleOption(0xaa123456), "aa123456");
        assertOption(new SpellParticleOption(0xaa123456, 2f), "aa12345640000000");
        assertOption(new PowerParticleOption(2f), "40000000");
        assertOption(new SculkChargeParticleOptions(2f), "40000000");
        assertOption(new ShriekParticleOption(300), "ac02");
        assertOption(new GeyserParticleOptions(300), "0000012c");
        assertOption(new GeyserBaseParticleOptions(300, 2f), "0000012c40000000");
        assertOption(new TrailParticleOption(1.25, -2.5, 3.75, 0x123456, 300),
                "3ff4000000000000c004000000000000400e00000000000000123456ac02");
    }

    @Test
    public void olderProtocolLayoutsRemainSupported() {
        for (int version = 754; version <= 776; version++) {
            ByteBuf out = Unpooled.buffer();
            try {
                assertEquals(version, write(version, out, sample(ParticleType.FLAME, null)));
                assertEquals(Mappings.getPacketId(version), readVarInt(out));
                int particleId = Mappings.getParticleId(ParticleType.FLAME, version) >>> 16;
                if (version <= 758) assertEquals(particleId, out.readInt());
                else if (version <= 765) assertEquals(particleId, readVarInt(out));
                assertTrue(out.readBoolean());
                if (version >= 769) assertFalse(out.readBoolean());
                assertEquals(1.25, out.readDouble(), 0);
                assertEquals(-2.5, out.readDouble(), 0);
                assertEquals(3.75, out.readDouble(), 0);
                assertEquals(.125f, out.readFloat(), 0);
                assertEquals(.25f, out.readFloat(), 0);
                assertEquals(.375f, out.readFloat(), 0);
                assertEquals(.5f, out.readFloat(), 0);
                assertEquals(300, out.readInt());
                if (version >= 766) assertEquals(particleId, readVarInt(out));
                assertFalse(out.isReadable());
            } finally {
                out.release();
            }
        }
    }

    @Test
    public void encoderFramesMultiplePacketsWithAndWithoutCompression() {
        for (boolean compress : new boolean[]{false, true}) {
            EmbeddedChannel channel = new EmbeddedChannel();
            if (compress) channel.pipeline().addLast("compress", new ChannelOutboundHandlerAdapter());
            channel.pipeline().addLast(new ParticleEncoder(channel, UUID.randomUUID(), 777));
            try {
                ParticleData particle = sample(ParticleType.DUST,
                        new DustParticleOptions(0x123456, 1.5f));
                channel.writeOutbound(particle, particle);
                for (int i = 0; i < 2; i++) {
                    ByteBuf frame = channel.readOutbound();
                    try {
                        assertEquals(frame.readableBytes() - 2, readVarInt(frame));
                        if (compress) assertEquals(0, readVarInt(frame));
                        ByteBuf expected = Unpooled.buffer();
                        try {
                            ParticleWriter.write(777, expected, particle, 0, 0, 0,
                                    particle.xDist, particle.yDist, particle.zDist);
                            assertArrayEquals(bytes(expected), bytes(frame));
                        } finally {
                            expected.release();
                        }
                    } finally {
                        frame.release();
                    }
                }
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    @Test
    public void encoderRejectsFrameTooLargeForTwoByteLength() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ParticleEncoder encoder = new ParticleEncoder(channel, UUID.randomUUID(), 777);
        ByteBuf out = Unpooled.buffer();
        try {
            encoder.setOut(out);
            ParticleOption oversized = new ParticleOption() {
                public void write(ByteBuf buffer, int version) { buffer.writeZero(16384); }
                public boolean writable(int version) { return true; }
                public ParticleOptionType getType() { return ParticleOptionType.DUST_PARTICLE_OPTIONS; }
            };
            encoder.append(sample(ParticleType.DUST, oversized), 0, 0, 0, 0, 0, 0);
            assertEquals(0, out.writerIndex());
        } finally {
            encoder.setOut(null);
            out.release();
            channel.finishAndReleaseAll();
        }
    }

    private static ParticleData sample(ParticleType type, ParticleOption option) {
        return ParticleData.builder().particle(type).data(option).overrideLimiter(true)
                .alwaysShow(false).maxSpeed(.5f).count(300)
                .xDist(.125f).yDist(.25f).zDist(.375f).build();
    }

    private static int write(int version, ByteBuf out, ParticleData particle) {
        return ParticleWriter.write(version, out, particle, 1.25, -2.5, 3.75,
                particle.xDist, particle.yDist, particle.zDist);
    }

    private static void assertPacket(ParticleType type, ParticleOption option, String expected) {
        ByteBuf out = Unpooled.buffer();
        try {
            assertEquals(777, write(777, out, sample(type, option)));
            assertArrayEquals(hex(expected), bytes(out));
        } finally {
            out.release();
        }
    }

    private static void assertOption(ParticleOption option, String expected) {
        ByteBuf out = Unpooled.buffer();
        try {
            assertTrue(option.writable(777));
            option.write(out, 777);
            assertArrayEquals(hex(expected), bytes(out));
        } finally {
            out.release();
        }
    }

    private static byte[] bytes(ByteBuf out) {
        byte[] result = new byte[out.readableBytes()];
        out.readBytes(result);
        return result;
    }

    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static int readVarInt(ByteBuf out) {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int value = out.readUnsignedByte();
            result |= (value & 127) << shift;
            if ((value & 128) == 0) return result;
        }
        throw new AssertionError("Invalid VarInt");
    }
}
