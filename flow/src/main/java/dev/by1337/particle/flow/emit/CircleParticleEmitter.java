package dev.by1337.particle.flow.emit;

import dev.by1337.particle.flow.connection.Connections;
import dev.by1337.particle.flow.connection.ParticleEmitterConditionPair;
import dev.by1337.particle.flow.util.Vec3d;
import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import dev.by1337.yaml.decoder.k2v.LookupDecoder;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The local circle lies in XZ, starting at +X and continuing toward +Z.
 * YAML rotation is X;Y;Z in degrees. Positive angles follow the right-hand rule;
 * rotations are applied around fixed axes in X, then Y, then Z order (Rz * Ry * Rx).
 * For example, +90 X maps local +Z to -Y, and +90 Y maps local +X to -Z.
 * Offsets are added to the incoming base position and are not rotated.
 */
public final class CircleParticleEmitter implements ParticleEmitter {
    public static final YamlDecoder<CircleParticleEmitter> DECODER = RecordYamlDecoder.mapOf(
            CircleParticleEmitter::new,
            YamlDecoder.INT.fieldOf("points"),
            YamlDecoder.DOUBLE.fieldOf("radius"),
            Vec3d.DECODER.fieldOf("offsets"),
            Vec3d.DECODER.fieldOf("rotation", Vec3d.ZERO),
            DistType.DECODER.fieldOf("dist", DistType.NONE),
            Connections.DECODER.fieldOf("connections")
    );

    private final int points;
    private final double radius;
    private final @Nullable Vec3d offsets;
    private final double[] radialX;
    private final double[] radialY;
    private final double[] radialZ;
    private final DistType dist;
    private final Connections connectionsMap;
    private ParticleEmitterConditionPair[] connections;

    public CircleParticleEmitter(int points, double radius, DistType dist, Connections connectionsMap) {
        this(points, radius, null, Vec3d.ZERO, dist, connectionsMap);
    }

    public CircleParticleEmitter(int points, double radius, @Nullable Vec3d offsets, Vec3d rotation,
                                 DistType dist, Connections connectionsMap) {
        if (points <= 0) throw new IllegalArgumentException("points must be positive");
        if (!Double.isFinite(radius) || radius < 0) throw new IllegalArgumentException("radius must be finite and non-negative");
        EulerRotation transform = new EulerRotation(rotation);
        if (offsets != null && !EulerRotation.finite(offsets)) throw new IllegalArgumentException("offsets must be finite");
        this.points = points;
        this.radius = radius;
        this.offsets = offsets;
        this.dist = Objects.requireNonNull(dist, "dist");
        this.connectionsMap = Objects.requireNonNull(connectionsMap, "connections");
        connections = new ParticleEmitterConditionPair[0];

        radialX = new double[points];
        radialY = new double[points];
        radialZ = new double[points];
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            radialX[i] = Math.cos(angle);
            radialZ[i] = Math.sin(angle);
        }
        transform.rotate(radialX, radialY, radialZ);
    }

    @Override
    public void link(EmitterGraph graph) {
        connections = connectionsMap.toPairArray(graph);
    }

    @Override
    public void emit(EmitContext ctx, PacketBuilder out, double baseX, double baseY, double baseZ, float xDist, float yDist, float zDist) {
        ctx.pushDepth();
        try {
            double centerX = baseX + (offsets == null ? 0 : offsets.x());
            double centerY = baseY + (offsets == null ? 0 : offsets.y());
            double centerZ = baseZ + (offsets == null ? 0 : offsets.z());
            for (int i = 0; i < points; i++) {
                double rx = radialX[i];
                double ry = radialY[i];
                double rz = radialZ[i];
                double x = centerX + radius * rx;
                double y = centerY + radius * ry;
                double z = centerZ + radius * rz;
                float pointXDist = xDist;
                float pointYDist = yDist;
                float pointZDist = zDist;
                if (dist != DistType.NONE) {
                    float sign = dist == DistType.OUTWARD ? 1 : -1;
                    pointXDist = sign * (float) rx;
                    pointYDist = sign * (float) ry;
                    pointZDist = sign * (float) rz;
                }
                for (ParticleEmitterConditionPair pair : connections) {
                    if (!pair.test(ctx.tick())) continue;
                    pair.emit(ctx, out, x, y, z, pointXDist, pointYDist, pointZDist);
                }
            }
        } finally {
            ctx.popDepth();
        }
    }

    public enum DistType{
        NONE,
        OUTWARD,
        INWARD;
        public static final YamlDecoder<DistType> DECODER = LookupDecoder.fromEnum(DistType.values());
    }
}
