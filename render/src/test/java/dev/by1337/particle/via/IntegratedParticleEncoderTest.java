package dev.by1337.particle.via;

import dev.by1337.leaf.api.network.PacketOutput;
import dev.by1337.particle.ParticleType;
import dev.by1337.particle.netty.handler.IntegratedParticleEncoder;
import dev.by1337.particle.particle.ParticleData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class IntegratedParticleEncoderTest {
    @Test
    public void bodiesMatchExistingWriterAcrossSupportedClients() {
        for (int protocol : new int[]{ 769, 770, 771, 772, 773, 774, 775, 776, 777 }) {
            var particle = ParticleData.builder().particle(ParticleType.FLAME).count(3).build();
            ByteBuf expected = Unpooled.buffer(), actual = Unpooled.buffer();
            try {
                assertEquals(protocol, ParticleWriter.write(protocol, expected, particle, 1, 2, 3, particle.xDist, particle.yDist, particle.zDist));
                IntegratedParticleEncoder.write(particle.shift(1, 2, 3), output(protocol, actual), UUID.randomUUID());
                assertEquals(expected, actual);
            } finally {
                expected.release();
                actual.release();
            }
        }
    }

    @Test
    public void unknownClientFallsBackToServerEncodingForVia() {
        ByteBuf actual = Unpooled.buffer(), expected = Unpooled.buffer();
        var particle = ParticleData.builder().particle(ParticleType.FLAME).count(1).build();
        try {
            PacketOutput output = new PacketOutput() {
                public int clientProtocol() {
                    return 10000;
                }

                public int serverProtocol() {
                    return 774;
                }

                public void write(Body body) throws Exception {
                    assertEquals(774, body.encode(actual, clientProtocol()));
                }
            };
            IntegratedParticleEncoder.write(particle, output, UUID.randomUUID());
            assertEquals(774, ParticleWriter.write(774, expected, particle, 0, 0, 0, particle.xDist, particle.yDist, particle.zDist));
            assertEquals(expected, actual);
        } finally {
            actual.release();
            expected.release();
        }
    }

    private static PacketOutput output(int protocol, ByteBuf target) {
        return new PacketOutput() {
            public int clientProtocol() {
                return protocol;
            }

            public int serverProtocol() {
                return 774;
            }

            public void write(Body body) throws Exception {
                assertEquals(protocol, body.encode(target, protocol));
            }
        };
    }
}
