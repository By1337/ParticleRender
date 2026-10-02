package dev.by1337.particle.flow.connection;

import dev.by1337.particle.flow.emit.EmitContext;
import dev.by1337.particle.flow.emit.ParticleEmitter;
import dev.by1337.particle.particle.PacketBuilder;

import java.util.BitSet;

public record ParticleEmitterConditionPair(ParticleEmitter emitter, ConnectionCondition condition) {
    public boolean test(int tick) {
        return condition.test(tick);
    }

    public void emit(EmitContext ctx, PacketBuilder out, double baseX, double baseY, double baseZ, float xDist, float yDist, float zDist) {
        emitter.emit(ctx, out, baseX, baseY, baseZ, xDist, yDist, zDist);
    }

    public void executionKey(int key, BitSet set) {
        emitter.executionKey(key, set);
    }
}
