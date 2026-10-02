package dev.by1337.particle.flow.emit;

import dev.by1337.particle.flow.connection.Connections;
import dev.by1337.particle.flow.connection.ParticleEmitterConditionPair;
import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

import java.util.Objects;

/** Passes the incoming position and dist to configured root connections. */
public final class RootParticleEmitter implements ParticleEmitter {
    public static final YamlDecoder<RootParticleEmitter> DECODER = RecordYamlDecoder.mapOf(
            RootParticleEmitter::new, Connections.DECODER.fieldOf("connections"));

    private final Connections connectionsMap;
    private ParticleEmitterConditionPair[] connections = new ParticleEmitterConditionPair[0];

    public RootParticleEmitter(Connections connectionsMap) {
        this.connectionsMap = Objects.requireNonNull(connectionsMap, "connections");
    }

    @Override
    public void link(EmitterGraph graph) {
        connections = connectionsMap.toPairArray(graph);
    }

    @Override
    public void emit(EmitContext ctx, PacketBuilder out, double baseX, double baseY, double baseZ,
                     float xDist, float yDist, float zDist) {
        ctx.pushDepth();
        try {
            for (ParticleEmitterConditionPair pair : connections) {
                if (pair.test(ctx.tick())) {
                    pair.emit(ctx, out, baseX, baseY, baseZ, xDist, yDist, zDist);
                }
            }
        } finally {
            ctx.popDepth();
        }
    }
}
