package dev.by1337.particle.netty.handler;

import dev.by1337.leaf.api.network.PacketOutput;
import dev.by1337.particle.hook.ThemeColorContext;
import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.particle.particle.ParticleData;
import dev.by1337.particle.particle.ParticleSource;
import dev.by1337.particle.via.ParticleWriter;
import io.netty.buffer.ByteBuf;
import java.util.UUID;

/** One event-loop-scoped encoder; reuses its body callback for every particle in the source. */
public final class IntegratedParticleEncoder implements PacketBuilder, PacketOutput.Body {
    private final PacketOutput output;
    private ParticleData particle;
    private double x, y, z;
    private float xDist, yDist, zDist;

    private IntegratedParticleEncoder(PacketOutput output) {
        this.output = output;
    }

    public static void write(ParticleSource source, PacketOutput output, UUID player) {
        try (var color = ThemeColorContext.push(ThemeColorContext.resolve(player))) {
            source.doWrite(new IntegratedParticleEncoder(output), 0, 0, 0);
        }
    }

    @Override
    public void append(ParticleData particle, double x, double y, double z, float xDist, float yDist, float zDist) {
        this.particle = particle;
        this.x = x;
        this.y = y;
        this.z = z;
        this.xDist = xDist;
        this.yDist = yDist;
        this.zDist = zDist;
        try {
            output.write(this);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot encode particle", failure);
        }
    }

    @Override
    public int encode(ByteBuf buffer, int protocol) {
        int start = buffer.writerIndex();
        int written = ParticleWriter.write(protocol, buffer, particle, x, y, z, xDist, yDist, zDist);
        if (written == -1 && protocol != output.serverProtocol()) {
            buffer.writerIndex(start);
            return ParticleWriter.write(output.serverProtocol(), buffer, particle, x, y, z, xDist, yDist, zDist);
        }
        return written;
    }
}
