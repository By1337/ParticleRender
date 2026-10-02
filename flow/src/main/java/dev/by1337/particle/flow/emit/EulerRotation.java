package dev.by1337.particle.flow.emit;

import dev.by1337.particle.flow.util.Vec3d;

import java.util.Objects;

/** Fixed-axis, right-handed X then Y then Z rotation, with angles in degrees. */
final class EulerRotation {
    private final double m00, m01, m02;
    private final double m10, m11, m12;
    private final double m20, m21, m22;

    EulerRotation(Vec3d degrees) {
        Objects.requireNonNull(degrees, "rotation");
        if (!finite(degrees)) throw new IllegalArgumentException("rotation must be finite");
        double x = Math.toRadians(degrees.x() % 360);
        double y = Math.toRadians(degrees.y() % 360);
        double z = Math.toRadians(degrees.z() % 360);
        double sx = Math.sin(x), cx = Math.cos(x);
        double sy = Math.sin(y), cy = Math.cos(y);
        double sz = Math.sin(z), cz = Math.cos(z);
        m00 = cz * cy;
        m01 = cz * sy * sx - sz * cx;
        m02 = cz * sy * cx + sz * sx;
        m10 = sz * cy;
        m11 = sz * sy * sx + cz * cx;
        m12 = sz * sy * cx - cz * sx;
        m20 = -sy;
        m21 = cy * sx;
        m22 = cy * cx;
    }

    static boolean finite(Vec3d vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z());
    }

    void rotate(double[] xs, double[] ys, double[] zs) {
        for (int i = 0; i < xs.length; i++) {
            double x = xs[i], y = ys[i], z = zs[i];
            xs[i] = m00 * x + m01 * y + m02 * z;
            ys[i] = m10 * x + m11 * y + m12 * z;
            zs[i] = m20 * x + m21 * y + m22 * z;
        }
    }
}
