package dev.by1337.particle.particle.options;

import dev.by1337.particle.ParticleType;
import dev.by1337.particle.particle.ParticleData;
import dev.by1337.particle.via.ParticleWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.*;

public class VibrationParticleOptionTest {
    private static final VibrationParticleOption.BlockPos ORIGIN = new VibrationParticleOption.BlockPos(-1, 64, 2);
    private static final VibrationParticleOption.BlockPos TARGET = new VibrationParticleOption.BlockPos(3, 70, -4);

    @Test
    public void blockDestinationAcrossWireFormats() {
        VibrationParticleOption option = VibrationParticleOption.toBlock(ORIGIN, TARGET, 30);
        // Packed BlockPos: X in 26 bits, Z in 26 bits, Y in 12 bits.
        String origin = "ffffffc000002040";
        String target = "000000ffffffc046";
        for (int version = 755; version <= 758; version++) {
            assertEncoded(option, version, origin + "0f6d696e6563726166743a626c6f636b" + target + "1e");
        }
        for (int version = 759; version <= 765; version++) {
            assertEncoded(option, version, "0f6d696e6563726166743a626c6f636b" + target + "1e");
        }
        for (int version = 766; version <= 777; version++) {
            assertEncoded(option, version, "00" + target + "1e");
        }
        assertFalse(option.writable(754));
    }

    @Test
    public void entityDestinationChangesAt759() {
        VibrationParticleOption old = VibrationParticleOption.toEntity(ORIGIN, 300, 0, 40);
        VibrationParticleOption modern = VibrationParticleOption.toEntity(ORIGIN, 300, 1.25f, 40);
        assertEncoded(old, 755, "ffffffc000002040106d696e6563726166743a656e74697479ac0228");
        assertFalse(modern.writable(758));
        assertEncoded(modern, 759, "106d696e6563726166743a656e74697479ac023fa0000028");
        assertEncoded(modern, 766, "01ac023fa0000028");
        assertEncoded(modern, 777, "01ac023fa0000028");
    }

    @Test
    public void mappedParticleWritesForEverySupportedProtocol() {
        VibrationParticleOption option = VibrationParticleOption.toBlock(ORIGIN, TARGET, 30);
        ParticleData data = ParticleData.of(ParticleType.VIBRATION, option);
        for (int version = 755; version <= 777; version++) {
            ByteBuf out = Unpooled.buffer();
            try {
                assertEquals("protocol " + version, version,
                        ParticleWriter.write(version, out, data, -0.5, 64.5, 2.5, 0, 0, 0));
                assertTrue(out.isReadable());
            } finally {
                out.release();
            }
        }
    }

    private static void assertEncoded(VibrationParticleOption option, int version, String expectedHex) {
        ByteBuf out = Unpooled.buffer();
        try {
            option.write(out, version);
            byte[] bytes = new byte[out.readableBytes()];
            out.readBytes(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b & 0xff));
            assertEquals("protocol " + version, expectedHex, hex.toString());
        } finally {
            out.release();
        }
    }
}
