package dev.by1337.particle.flow.emit;

import dev.by1337.particle.flow.connection.Connections;
import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

import java.util.BitSet;

/** Wraps the incoming tick into an inclusive range for this node's descendants. */
public final class TickModuloParticleEmitter implements ParticleEmitter {
    public static final YamlDecoder<TickModuloParticleEmitter> DECODER = RecordYamlDecoder.mapOf(
            TickModuloParticleEmitter::new,
            YamlDecoder.INT.fieldOf("min", 0),
            YamlDecoder.INT.fieldOf("max"),
            Connections.DECODER.fieldOf("connections"));

    private final int min;
    private final long period;
    private final RootParticleEmitter delegate;

    public TickModuloParticleEmitter(int min, int max, Connections connections) {
        if (min < 0 || max < min) throw new IllegalArgumentException("Tick range must satisfy 0 <= min <= max");
        this.min = min;
        period = (long) max - min + 1;
        delegate = new RootParticleEmitter(connections);
    }

    private int wrap(int tick) {
        return (int) (min + Math.floorMod((long) tick - min, period));
    }

    @Override
    public void link(EmitterGraph graph, int id) {
        delegate.link(graph, id);
    }

    @Override
    public void executionKey(int tick, BitSet set) {
        delegate.executionKey(wrap(tick), set);
    }

    @Override
    public void emit(EmitContext ctx, PacketBuilder out, double baseX, double baseY, double baseZ,
        float xDist, float yDist, float zDist) {
        int previous = ctx.tick();
        ctx.tick(wrap(previous));
        try {
            delegate.emit(ctx, out, baseX, baseY, baseZ, xDist, yDist, zDist);
        } finally {
            ctx.tick(previous);
        }
    }
}
