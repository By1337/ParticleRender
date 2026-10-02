package dev.by1337.particle.flow.emit;

import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.yaml.decoder.YamlDecoder;

import java.util.BitSet;

public interface ParticleEmitter {
    YamlDecoder<ParticleEmitter> DECODER = ParticleEmitterRegistry.DEFAULT.decoder();

    void emit(EmitContext ctx, PacketBuilder out, double baseX, double baseY, double baseZ, float xDist, float yDist, float zDist);

    void link(EmitterGraph graph, int id);
    void executionKey(int tick, BitSet set);
}
