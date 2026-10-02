package dev.by1337.particle.flow.emit;

import dev.by1337.particle.flow.connection.Connections;
import dev.by1337.particle.flow.connection.ParticleEmitterConditionPair;
import dev.by1337.particle.flow.util.Vec3d;
import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Objects;

import static dev.by1337.particle.flow.emit.CircleParticleEmitter.DistType;

/** Local line runs along X. At its center, OUTWARD uses local +X. */
public final class LineParticleEmitter implements ParticleEmitter {
    public static final YamlDecoder<LineParticleEmitter> DECODER = RecordYamlDecoder.mapOf(
            LineParticleEmitter::new,
            YamlDecoder.INT.fieldOf("points"),
            YamlDecoder.DOUBLE.fieldOf("length"),
            Vec3d.DECODER.fieldOf("offsets"),
            Vec3d.DECODER.fieldOf("rotation", Vec3d.ZERO),
            DistType.DECODER.fieldOf("dist", DistType.NONE),
            Connections.DECODER.fieldOf("connections")
    );

    private final int points;
    private final @Nullable Vec3d offsets;
    private final DistType dist;
    private final Connections connectionsMap;
    private final double[] pointX, pointY, pointZ;
    private final double[] directionX, directionY, directionZ;
    private ParticleEmitterConditionPair[] connections = new ParticleEmitterConditionPair[0];
    private int id;

    public LineParticleEmitter(int points, double length, @Nullable Vec3d offsets, Vec3d rotation,
                               DistType dist, Connections connectionsMap) {
        if (points <= 0) throw new IllegalArgumentException("points must be positive");
        if (!Double.isFinite(length) || length < 0) throw new IllegalArgumentException("length must be finite and non-negative");
        if (offsets != null && !EulerRotation.finite(offsets)) throw new IllegalArgumentException("offsets must be finite");
        EulerRotation transform = new EulerRotation(rotation);
        this.points = points;
        this.offsets = offsets;
        this.dist = Objects.requireNonNull(dist, "dist");
        this.connectionsMap = Objects.requireNonNull(connectionsMap, "connections");

        pointX = new double[points];
        pointY = new double[points];
        pointZ = new double[points];
        directionX = new double[points];
        directionY = new double[points];
        directionZ = new double[points];
        for (int i = 0; i < points; i++) {
            double along = points == 1 ? 0 : length * ((double) i / (points - 1) - 0.5);
            pointX[i] = along;
            directionX[i] = along < 0 ? -1 : 1;
        }
        transform.rotate(pointX, pointY, pointZ);
        transform.rotate(directionX, directionY, directionZ);
    }

    @Override
    public void link(EmitterGraph graph, int id) {
        this.id = id;
        connections = connectionsMap.toPairArray(graph);
    }
    @Override
    public void executionKey(int tick, BitSet set) {
        set.set(id);
        for (ParticleEmitterConditionPair pair : connections) {
            if (pair.test(tick)) pair.executionKey(tick, set);
        }
    }

    @Override
    public void emit(EmitContext ctx, PacketBuilder out, double baseX, double baseY, double baseZ,
                     float xDist, float yDist, float zDist) {
        ctx.pushDepth();
        try {
            double centerX = baseX + (offsets == null ? 0 : offsets.x());
            double centerY = baseY + (offsets == null ? 0 : offsets.y());
            double centerZ = baseZ + (offsets == null ? 0 : offsets.z());
            for (int i = 0; i < points; i++) {
                double x = centerX + pointX[i];
                double y = centerY + pointY[i];
                double z = centerZ + pointZ[i];
                float dx = xDist, dy = yDist, dz = zDist;
                if (dist != DistType.NONE) {
                    float sign = dist == DistType.OUTWARD ? 1 : -1;
                    dx = sign * (float) directionX[i];
                    dy = sign * (float) directionY[i];
                    dz = sign * (float) directionZ[i];
                }
                for (ParticleEmitterConditionPair pair : connections) {
                    if (pair.test(ctx.tick())) pair.emit(ctx, out, x, y, z, dx, dy, dz);
                }
            }
        } finally {
            ctx.popDepth();
        }
    }
}
