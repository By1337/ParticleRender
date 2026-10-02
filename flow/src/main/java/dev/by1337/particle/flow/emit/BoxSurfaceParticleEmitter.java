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

/** Samples all six faces by area; size is the full X/Y/Z extent. */
public final class BoxSurfaceParticleEmitter implements ParticleEmitter {
    public static final YamlDecoder<BoxSurfaceParticleEmitter> DECODER = RecordYamlDecoder.mapOf(
            BoxSurfaceParticleEmitter::new,
            YamlDecoder.INT.fieldOf("points"),
            Vec3d.DECODER.fieldOf("size"),
            Vec3d.DECODER.fieldOf("offsets"),
            Vec3d.DECODER.fieldOf("rotation", Vec3d.ZERO),
            DistType.DECODER.fieldOf("dist", DistType.NONE),
            Connections.DECODER.fieldOf("connections")
    );
    private static final double GOLDEN_RATIO_FRACTION = (Math.sqrt(5) - 1) / 2;

    private final int points;
    private final @Nullable Vec3d offsets;
    private final DistType dist;
    private final Connections connectionsMap;
    private final double[] pointX, pointY, pointZ;
    private final double[] normalX, normalY, normalZ;
    private ParticleEmitterConditionPair[] connections = new ParticleEmitterConditionPair[0];

    public BoxSurfaceParticleEmitter(int points, Vec3d size, @Nullable Vec3d offsets, Vec3d rotation,
                                     DistType dist, Connections connectionsMap) {
        if (points <= 0) throw new IllegalArgumentException("points must be positive");
        Objects.requireNonNull(size, "size");
        if (!EulerRotation.finite(size) || size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
            throw new IllegalArgumentException("size components must be finite and positive");
        }
        if (offsets != null && !EulerRotation.finite(offsets)) throw new IllegalArgumentException("offsets must be finite");
        EulerRotation transform = new EulerRotation(rotation);
        double xy = size.x() * size.y();
        double xz = size.x() * size.z();
        double yz = size.y() * size.z();
        double totalArea = 2 * (xy + xz + yz);
        if (!Double.isFinite(totalArea) || totalArea <= 0) throw new IllegalArgumentException("box surface area is invalid");
        this.points = points;
        this.offsets = offsets;
        this.dist = Objects.requireNonNull(dist, "dist");
        this.connectionsMap = Objects.requireNonNull(connectionsMap, "connections");

        pointX = new double[points];
        pointY = new double[points];
        pointZ = new double[points];
        normalX = new double[points];
        normalY = new double[points];
        normalZ = new double[points];

        double[] faceAreas = {yz, yz, xz, xz, xy, xy};
        int[] counts = new int[6];
        int face = 0;
        double cumulative = faceAreas[0];
        for (int i = 0; i < points; i++) {
            double areaPosition = totalArea * ((i + 0.5) / points);
            while (face < 5 && areaPosition >= cumulative) cumulative += faceAreas[++face];
            counts[face]++;
        }

        double hx = size.x() / 2, hy = size.y() / 2, hz = size.z() / 2;
        int index = 0;
        for (face = 0; face < 6; face++) {
            int count = counts[face];
            for (int j = 0; j < count; j++) {
                double u = 2 * (j + 0.5) / count - 1;
                double v = count == 1 ? 0 : 2 * fractional((j + 0.5) * GOLDEN_RATIO_FRACTION) - 1;
                switch (face) {
                    case 0 -> { pointX[index] = hx; pointY[index] = u * hy; pointZ[index] = v * hz; normalX[index] = 1; }
                    case 1 -> { pointX[index] = -hx; pointY[index] = u * hy; pointZ[index] = v * hz; normalX[index] = -1; }
                    case 2 -> { pointX[index] = u * hx; pointY[index] = hy; pointZ[index] = v * hz; normalY[index] = 1; }
                    case 3 -> { pointX[index] = u * hx; pointY[index] = -hy; pointZ[index] = v * hz; normalY[index] = -1; }
                    case 4 -> { pointX[index] = u * hx; pointY[index] = v * hy; pointZ[index] = hz; normalZ[index] = 1; }
                    case 5 -> { pointX[index] = u * hx; pointY[index] = v * hy; pointZ[index] = -hz; normalZ[index] = -1; }
                    default -> throw new AssertionError(face);
                }
                index++;
            }
        }
        transform.rotate(pointX, pointY, pointZ);
        transform.rotate(normalX, normalY, normalZ);
    }

    private static double fractional(double value) {
        return value - Math.floor(value);
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
                double x = centerX + pointX[i];
                double y = centerY + pointY[i];
                double z = centerZ + pointZ[i];
                float dx = xDist, dy = yDist, dz = zDist;
                if (dist != DistType.NONE) {
                    float sign = dist == DistType.OUTWARD ? 1 : -1;
                    dx = sign * (float) normalX[i];
                    dy = sign * (float) normalY[i];
                    dz = sign * (float) normalZ[i];
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
