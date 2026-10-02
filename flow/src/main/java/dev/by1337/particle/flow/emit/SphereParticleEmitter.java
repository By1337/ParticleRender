package dev.by1337.particle.flow.emit;

import dev.by1337.particle.flow.connection.Connections;
import dev.by1337.particle.flow.connection.ParticleEmitterConditionPair;
import dev.by1337.particle.flow.util.Vec3d;
import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static dev.by1337.particle.flow.emit.CircleParticleEmitter.DistType;

/** A deterministic Fibonacci sphere, using the same X/Y/Z Euler convention as Circle. */
public final class SphereParticleEmitter implements ParticleEmitter {
    public static final YamlDecoder<SphereParticleEmitter> DECODER = RecordYamlDecoder.mapOf(
            SphereParticleEmitter::new,
            YamlDecoder.INT.fieldOf("points"),
            YamlDecoder.DOUBLE.fieldOf("radius"),
            Vec3d.DECODER.fieldOf("offsets"),
            Vec3d.DECODER.fieldOf("rotation", Vec3d.ZERO),
            DistType.DECODER.fieldOf("dist", DistType.NONE),
            Connections.DECODER.fieldOf("connections")
    );
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    private final int points;
    private final double radius;
    private final @Nullable Vec3d offsets;
    private final DistType dist;
    private final Connections connectionsMap;
    private final double[] radialX, radialY, radialZ;
    private ParticleEmitterConditionPair[] connections = new ParticleEmitterConditionPair[0];

    public SphereParticleEmitter(int points, double radius, @Nullable Vec3d offsets, Vec3d rotation,
                                 DistType dist, Connections connectionsMap) {
        if (points <= 0) throw new IllegalArgumentException("points must be positive");
        if (!Double.isFinite(radius) || radius < 0) throw new IllegalArgumentException("radius must be finite and non-negative");
        if (offsets != null && !EulerRotation.finite(offsets)) throw new IllegalArgumentException("offsets must be finite");
        EulerRotation transform = new EulerRotation(rotation);
        this.points = points;
        this.radius = radius;
        this.offsets = offsets;
        this.dist = Objects.requireNonNull(dist, "dist");
        this.connectionsMap = Objects.requireNonNull(connectionsMap, "connections");

        radialX = new double[points];
        radialY = new double[points];
        radialZ = new double[points];
        for (int i = 0; i < points; i++) {
            double y = 1 - 2 * (i + 0.5) / points;
            double ringRadius = Math.sqrt(1 - y * y);
            double angle = GOLDEN_ANGLE * i;
            radialX[i] = Math.cos(angle) * ringRadius;
            radialY[i] = y;
            radialZ[i] = Math.sin(angle) * ringRadius;
        }
        transform.rotate(radialX, radialY, radialZ);
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
            double centerX = baseX + (offsets == null ? 0 : offsets.x());
            double centerY = baseY + (offsets == null ? 0 : offsets.y());
            double centerZ = baseZ + (offsets == null ? 0 : offsets.z());
            for (int i = 0; i < points; i++) {
                double rx = radialX[i], ry = radialY[i], rz = radialZ[i];
                double x = centerX + radius * rx;
                double y = centerY + radius * ry;
                double z = centerZ + radius * rz;
                float dx = xDist, dy = yDist, dz = zDist;
                if (dist != DistType.NONE) {
                    float sign = dist == DistType.OUTWARD ? 1 : -1;
                    dx = sign * (float) rx;
                    dy = sign * (float) ry;
                    dz = sign * (float) rz;
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
