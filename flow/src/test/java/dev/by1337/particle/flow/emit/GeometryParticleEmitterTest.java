package dev.by1337.particle.flow.emit;

import dev.by1337.particle.flow.connection.Connections;
import dev.by1337.particle.flow.util.Vec3d;
import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.yaml.YamlMap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static dev.by1337.particle.flow.emit.CircleParticleEmitter.DistType;
import static org.junit.Assert.*;

public class GeometryParticleEmitterTest {
    private static final PacketBuilder OUT = (particle, x, y, z, dx, dy, dz) -> { };
    private static final Vec3d OFFSET = new Vec3d(1, 2, 3);
    private static final Vec3d CENTER = new Vec3d(11, 22, 33);

    @Test
    public void sphereHasUniformSurfaceRadiusAndRotatedRadialDirections() {
        for (DistType type : DistType.values()) {
            List<double[]> points = run(new SphereParticleEmitter(128, 3, OFFSET,
                    new Vec3d(31, 47, 16), type, connections()));
            assertEquals(128, points.size());
            double averageX = 0, averageY = 0, averageZ = 0;
            for (double[] point : points) {
                double x = point[0] - CENTER.x();
                double y = point[1] - CENTER.y();
                double z = point[2] - CENTER.z();
                assertEquals(3, Math.sqrt(x * x + y * y + z * z), 1e-6);
                averageX += x;
                averageY += y;
                averageZ += z;
                assertRadialOrIncoming(type, point, CENTER);
            }
            assertEquals(0, averageX / points.size(), 0.1);
            assertEquals(0, averageY / points.size(), 0.1);
            assertEquals(0, averageZ / points.size(), 0.1);
        }

        double[] rotated = run(new SphereParticleEmitter(1, 2, null,
                new Vec3d(0, 90, 0), DistType.OUTWARD, connections())).get(0);
        assertPoint(rotated, 10, 20, 28, 0, 0, -1);
    }

    @Test
    public void lineIncludesEndpointsAndUsesDeterministicCenterDirection() {
        List<double[]> horizontal = run(new LineParticleEmitter(5, 4, null, Vec3d.ZERO,
                DistType.OUTWARD, connections()));
        assertEquals(5, horizontal.size());
        for (int i = 0; i < 5; i++) {
            double direction = i < 2 ? -1 : 1;
            assertPoint(horizontal.get(i), 8 + i, 20, 30, direction, 0, 0);
        }
        List<double[]> vertical = run(new LineParticleEmitter(5, 4, OFFSET,
                new Vec3d(0, 0, 90), DistType.INWARD, connections()));
        assertEquals(5, vertical.size());
        assertPoint(vertical.get(0), 11, 20, 33, 0, 1, 0);
        assertPoint(vertical.get(2), 11, 22, 33, 0, -1, 0);
        assertPoint(vertical.get(4), 11, 24, 33, 0, -1, 0);
        assertPoint(run(new LineParticleEmitter(1, 4, OFFSET, Vec3d.ZERO,
                DistType.OUTWARD, connections())).get(0), 11, 22, 33, 1, 0, 0);
        assertIncoming(run(new LineParticleEmitter(1, 4, OFFSET, Vec3d.ZERO,
                DistType.NONE, connections())).get(0));
    }

    @Test
    public void rectangleSamplesPerimeterWithoutDuplicateCorners() {
        List<double[]> points = run(new RectangleParticleEmitter(6, 4, 2, null,
                Vec3d.ZERO, DistType.OUTWARD, connections()));
        assertEquals(6, points.size());
        double[][] expected = {{8, 29}, {10, 29}, {12, 29}, {12, 31}, {10, 31}, {8, 31}};
        for (int i = 0; i < points.size(); i++) {
            double[] point = points.get(i);
            assertEquals(expected[i][0], point[0], 1e-6);
            assertEquals(20, point[1], 1e-6);
            assertEquals(expected[i][1], point[2], 1e-6);
            assertRadialOrIncoming(DistType.OUTWARD, point, new Vec3d(10, 20, 30));
        }
        List<double[]> rotated = run(new RectangleParticleEmitter(6, 4, 2, OFFSET,
                new Vec3d(90, 0, 0), DistType.INWARD, connections()));
        assertPoint(rotated.get(0), 9, 23, 33, 2 / Math.sqrt(5), -1 / Math.sqrt(5), 0);
        for (double[] point : rotated) {
            assertEquals(33, point[2], 1e-6);
            assertRadialOrIncoming(DistType.INWARD, point, CENTER);
        }
        assertIncoming(run(new RectangleParticleEmitter(6, 4, 2, null,
                Vec3d.ZERO, DistType.NONE, connections())).get(0));
    }

    @Test
    public void boxAllocatesSamplesByFaceAreaAndRotatesNormals() {
        List<double[]> points = run(new BoxSurfaceParticleEmitter(28, new Vec3d(4, 2, 1), null,
                Vec3d.ZERO, DistType.OUTWARD, connections()));
        assertEquals(28, points.size());
        int[] faceCounts = new int[6];
        for (double[] point : points) {
            double x = point[0] - 10, y = point[1] - 20, z = point[2] - 30;
            assertTrue(Math.abs(x) <= 2 + 1e-6);
            assertTrue(Math.abs(y) <= 1 + 1e-6);
            assertTrue(Math.abs(z) <= 0.5 + 1e-6);
            int face = point[3] != 0 ? (point[3] > 0 ? 0 : 1)
                    : point[4] != 0 ? (point[4] > 0 ? 2 : 3) : (point[5] > 0 ? 4 : 5);
            faceCounts[face]++;
            assertEquals(1, point[3] * point[3] + point[4] * point[4] + point[5] * point[5], 1e-6);
            assertEquals(face < 2 ? 2 : face < 4 ? 1 : 0.5,
                    face < 2 ? Math.abs(x) : face < 4 ? Math.abs(y) : Math.abs(z), 1e-6);
        }
        assertArrayEquals(new int[]{2, 2, 4, 4, 8, 8}, faceCounts);

        List<double[]> rotated = run(new BoxSurfaceParticleEmitter(6, new Vec3d(2, 2, 2), OFFSET,
                new Vec3d(90, 0, 0), DistType.OUTWARD, connections()));
        assertEquals(6, rotated.size());
        for (double[] point : rotated) {
            assertEquals(CENTER.x() + point[3], point[0], 1e-6);
            assertEquals(CENTER.y() + point[4], point[1], 1e-6);
            assertEquals(CENTER.z() + point[5], point[2], 1e-6);
        }
        assertPoint(rotated.get(2), 11, 22, 34, 0, 0, 1);
        List<double[]> inward = run(new BoxSurfaceParticleEmitter(6, new Vec3d(2, 2, 2), OFFSET,
                new Vec3d(90, 0, 0), DistType.INWARD, connections()));
        for (int i = 0; i < 6; i++) {
            assertEquals(-rotated.get(i)[3], inward.get(i)[3], 1e-6);
            assertEquals(-rotated.get(i)[4], inward.get(i)[4], 1e-6);
            assertEquals(-rotated.get(i)[5], inward.get(i)[5], 1e-6);
        }
        assertIncoming(run(new BoxSurfaceParticleEmitter(1, new Vec3d(2, 2, 2), null,
                Vec3d.ZERO, DistType.NONE, connections())).get(0));
    }

    @Test
    public void yamlDecodersUseSharedVectorAndDistFormats() {
        YamlMap sphere = config("points", 8, "radius", 2,
                "offsets", "1;2;3", "rotation", "20;30;10");
        assertNotNull(SphereParticleEmitter.DECODER.decode(sphere.get()).getOrThrow());
        YamlMap line = config("points", 1, "length", 4,
                "offsets", "1;2;3", "rotation", "0;0;90");
        assertNotNull(LineParticleEmitter.DECODER.decode(line.get()).getOrThrow());
        YamlMap rectangle = config("points", 8, "width", 4, "height", 2,
                "offsets", "1;2;3", "rotation", "90;0;0");
        assertNotNull(RectangleParticleEmitter.DECODER.decode(rectangle.get()).getOrThrow());
        YamlMap box = config("points", 12, "size", "4;2;1",
                "offsets", "1;2;3", "rotation", "20;30;10");
        assertNotNull(BoxSurfaceParticleEmitter.DECODER.decode(box.get()).getOrThrow());
    }

    @Test
    public void invalidGeometryIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SphereParticleEmitter(0, 2, null,
                Vec3d.ZERO, DistType.NONE, connections()));
        assertThrows(IllegalArgumentException.class, () -> new LineParticleEmitter(2, -1, null,
                Vec3d.ZERO, DistType.NONE, connections()));
        assertThrows(IllegalArgumentException.class, () -> new RectangleParticleEmitter(4, 2, 0,
                null, Vec3d.ZERO, DistType.NONE, connections()));
        assertThrows(IllegalArgumentException.class, () -> new BoxSurfaceParticleEmitter(4,
                new Vec3d(2, -1, 2), null, Vec3d.ZERO, DistType.NONE, connections()));
        assertThrows(IllegalArgumentException.class, () -> new SphereParticleEmitter(4, 2,
                new Vec3d(Double.NaN, 0, 0), Vec3d.ZERO, DistType.NONE, connections()));
        assertThrows(IllegalArgumentException.class, () -> new LineParticleEmitter(4, 2,
                null, new Vec3d(0, Double.POSITIVE_INFINITY, 0), DistType.NONE, connections()));
        assertTrue(BoxSurfaceParticleEmitter.DECODER.decode(
                config("points", 4, "size", "2;-1;2").get()).hasError());
    }

    private static YamlMap config(Object... fields) {
        YamlMap map = new YamlMap();
        for (int i = 0; i < fields.length; i += 2) map.set((String) fields[i], fields[i + 1]);
        map.set("dist", "outward");
        map.set("connections", Map.of("target", ""));
        return map;
    }

    private static Connections connections() {
        return new Connections(Map.of("target", tick -> true, "blocked", tick -> false));
    }

    private static List<double[]> run(ParticleEmitter emitter) {
        List<double[]> points = new ArrayList<>();
        ParticleEmitter target = new ParticleEmitter() {
            @Override
            public void emit(EmitContext ctx, PacketBuilder out, double x, double y, double z,
                             float dx, float dy, float dz) {
                points.add(new double[]{x, y, z, dx, dy, dz});
            }

            @Override
            public void link(EmitterGraph graph, int id) { }

            @Override
            public void executionKey(int tick, BitSet set) {
                set.set(0);
            }
        };
        EmitterGraph graph = new EmitterGraph(Map.of(
                "__root__", target, "target", target, "blocked", target));
        emitter.emit(new EmitContext(2, 0), OUT, 10, 20, 30, 0.25f, -0.5f, 0.75f);
        assertEquals(0, points.size());
        emitter.link(graph, 0);
        emitter.emit(new EmitContext(2, 0), OUT, 10, 20, 30, 0.25f, -0.5f, 0.75f);
        return points;
    }

    private static void assertRadialOrIncoming(DistType type, double[] point, Vec3d center) {
        if (type == DistType.NONE) {
            assertIncoming(point);
            return;
        }
        double x = point[0] - center.x(), y = point[1] - center.y(), z = point[2] - center.z();
        double length = Math.sqrt(x * x + y * y + z * z);
        double sign = type == DistType.OUTWARD ? 1 : -1;
        assertEquals(sign * x / length, point[3], 1e-6);
        assertEquals(sign * y / length, point[4], 1e-6);
        assertEquals(sign * z / length, point[5], 1e-6);
    }

    private static void assertIncoming(double[] point) {
        assertEquals(0.25, point[3], 1e-6);
        assertEquals(-0.5, point[4], 1e-6);
        assertEquals(0.75, point[5], 1e-6);
    }

    private static void assertPoint(double[] point, double x, double y, double z, double dx, double dy, double dz) {
        assertEquals(x, point[0], 1e-6);
        assertEquals(y, point[1], 1e-6);
        assertEquals(z, point[2], 1e-6);
        assertEquals(dx, point[3], 1e-6);
        assertEquals(dy, point[4], 1e-6);
        assertEquals(dz, point[5], 1e-6);
    }
}
