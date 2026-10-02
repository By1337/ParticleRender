package dev.by1337.particle.flow.emit;

import dev.by1337.particle.flow.connection.ConnectionCondition;
import dev.by1337.particle.flow.connection.Connections;
import dev.by1337.particle.flow.util.Vec3d;
import dev.by1337.particle.particle.PacketBuilder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CircleParticleEmitterTest {
    private static final PacketBuilder OUT = (particle, x, y, z, xDist, yDist, zDist) -> { };

    @Test
    public void emitsAtCirclePointsWithRadialDirection() {
        List<double[]> emitted = new ArrayList<>();
        ParticleEmitter child = new ParticleEmitter() {
            @Override
            public void emit(EmitContext ctx, PacketBuilder out, double x, double y, double z,
                             float xDist, float yDist, float zDist) {
                emitted.add(new double[]{x, y, z, xDist, yDist, zDist});
            }

            @Override
            public void link(EmitterGraph graph, int id) { }

            @Override
            public void executionKey(int tick, BitSet set) {
                set.set(0);
            }
        };
        EmitterGraph graph = new EmitterGraph(Map.of("__root__", child, "child", child));
        Connections connections = new Connections(Map.of("child", tick -> true));
        for (CircleParticleEmitter.DistType type : CircleParticleEmitter.DistType.values()) {
            CircleParticleEmitter circle = new CircleParticleEmitter(4, 2, type, connections);
            circle.link(graph, 0);
            emitted.clear();
            circle.emit(new EmitContext(2, 0), OUT, 10, 20, 30, 3, 4, 5);
            assertEquals(4, emitted.size());
            assertPoint(emitted.get(0), 12, 20, 30, type == CircleParticleEmitter.DistType.NONE ? 3 :
                    type == CircleParticleEmitter.DistType.OUTWARD ? 1 : -1,
                    type == CircleParticleEmitter.DistType.NONE ? 4 : 0,
                    type == CircleParticleEmitter.DistType.NONE ? 5 : 0);
            assertPoint(emitted.get(1), 10, 20, 32, type == CircleParticleEmitter.DistType.NONE ? 3 : 0,
                    type == CircleParticleEmitter.DistType.NONE ? 4 : 0,
                    type == CircleParticleEmitter.DistType.NONE ? 5 :
                            type == CircleParticleEmitter.DistType.OUTWARD ? 1 : -1);
        }

        CircleParticleEmitter shifted = new CircleParticleEmitter(4, 2,
                new Vec3d(1, 2, 3), Vec3d.ZERO, CircleParticleEmitter.DistType.NONE, connections);
        shifted.link(graph, 0);
        emitted.clear();
        shifted.emit(new EmitContext(2, 0), OUT, 10, 20, 30, 3, 4, 5);
        assertPoint(emitted.get(0), 13, 22, 33, 3, 4, 5);
        assertPoint(emitted.get(1), 11, 22, 35, 3, 4, 5);
    }

    @Test
    public void appliesConnectionConditionAndDecodesConfig() {
        final int[] calls = {0};
        ParticleEmitter child = new ParticleEmitter() {
            @Override
            public void emit(EmitContext ctx, PacketBuilder out, double x, double y, double z,
                             float xDist, float yDist, float zDist) {
                calls[0]++;
            }

            @Override
            public void link(EmitterGraph graph, int id) { }
            @Override
            public void executionKey(int tick, BitSet set) {
                set.set(0);
            }
        };
        EmitterGraph graph = new EmitterGraph(Map.of("__root__", child, "child", child));
        var yaml = new dev.by1337.yaml.YamlMap();
        yaml.set("points", 3);
        yaml.set("radius", 2);
        yaml.set("dist", "outward");
        yaml.set("connections", Map.of("child", "<0"));
        CircleParticleEmitter circle = CircleParticleEmitter.DECODER.decode(yaml.get()).getOrThrow();
        circle.link(graph, 0);
        circle.emit(new EmitContext(2, 0), OUT, 0, 0, 0, 0, 0, 0);
        assertEquals(0, calls[0]);

        CircleParticleEmitter always = new CircleParticleEmitter(3, 2, CircleParticleEmitter.DistType.NONE,
                new Connections(Map.of("child", ConnectionCondition.parse(""))));
        always.link(graph, 0);
        always.emit(new EmitContext(2, 0), OUT, 0, 0, 0, 0, 0, 0);
        assertEquals(3, calls[0]);
    }

    @Test
    public void offsetAndXRotationProduceVerticalCircle() {
        List<double[]> emitted = new ArrayList<>();
        ParticleEmitter child = new ParticleEmitter() {
            @Override
            public void emit(EmitContext ctx, PacketBuilder out, double x, double y, double z,
                             float xDist, float yDist, float zDist) {
                emitted.add(new double[]{x, y, z, xDist, yDist, zDist});
            }

            @Override
            public void link(EmitterGraph graph, int id) { }
            @Override
            public void executionKey(int tick, BitSet set) {
                set.set(0);
            }
        };
        EmitterGraph graph = new EmitterGraph(Map.of("__root__", child, "child", child));
        var yaml = new dev.by1337.yaml.YamlMap();
        yaml.set("points", 4);
        yaml.set("radius", 2);
        yaml.set("offsets", "1;2;3");
        yaml.set("rotation", "90;0;0");
        yaml.set("dist", "outward");
        yaml.set("connections", Map.of("child", ""));
        CircleParticleEmitter circle = CircleParticleEmitter.DECODER.decode(yaml.get()).getOrThrow();
        circle.link(graph, 0);
        circle.emit(new EmitContext(2, 0), OUT, 10, 20, 30, 3, 4, 5);
        assertEquals(4, emitted.size());
        assertPoint(emitted.get(0), 13, 22, 33, 1, 0, 0);
        assertPoint(emitted.get(1), 11, 20, 33, 0, -1, 0);
        assertPoint(emitted.get(3), 11, 24, 33, 0, 1, 0);
    }

    @Test
    public void arbitraryRotationPreservesRadiusAndAllDistModes() {
        List<double[]> emitted = new ArrayList<>();
        ParticleEmitter child = new ParticleEmitter() {
            @Override
            public void emit(EmitContext ctx, PacketBuilder out, double x, double y, double z,
                             float xDist, float yDist, float zDist) {
                emitted.add(new double[]{x, y, z, xDist, yDist, zDist});
            }

            @Override
            public void link(EmitterGraph graph, int id) { }
            @Override
            public void executionKey(int tick, BitSet set) {
                set.set(0);
            }
        };
        EmitterGraph graph = new EmitterGraph(Map.of("__root__", child, "child", child));
        double radius = 2.75;
        double centerX = 11, centerY = 18, centerZ = 33;
        Connections connections = new Connections(Map.of("child", tick -> true));
        for (CircleParticleEmitter.DistType type : CircleParticleEmitter.DistType.values()) {
            CircleParticleEmitter circle = new CircleParticleEmitter(17, radius,
                    new Vec3d(1, -2, 3), new Vec3d(33, 47, -21), type, connections);
            circle.link(graph, 0);
            emitted.clear();
            circle.emit(new EmitContext(2,0), OUT, 10, 20, 30, 0.25f, -0.5f, 0.75f);
            assertEquals(17, emitted.size());
            for (double[] point : emitted) {
                double dx = point[0] - centerX;
                double dy = point[1] - centerY;
                double dz = point[2] - centerZ;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                assertEquals(radius, length, 1e-6);
                if (type == CircleParticleEmitter.DistType.NONE) {
                    assertPoint(point, point[0], point[1], point[2], 0.25, -0.5, 0.75);
                } else {
                    double sign = type == CircleParticleEmitter.DistType.OUTWARD ? 1 : -1;
                    assertPoint(point, point[0], point[1], point[2],
                            sign * dx / length, sign * dy / length, sign * dz / length);
                }
            }
            double y = Math.toRadians(47);
            double z = Math.toRadians(-21);
            assertEquals(centerX + radius * Math.cos(y) * Math.cos(z), emitted.get(0)[0], 1e-6);
            assertEquals(centerY + radius * Math.cos(y) * Math.sin(z), emitted.get(0)[1], 1e-6);
            assertEquals(centerZ - radius * Math.sin(y), emitted.get(0)[2], 1e-6);
        }
    }

    private static void assertPoint(double[] actual, double x, double y, double z,
                                    double xDist, double yDist, double zDist) {
        assertEquals(x, actual[0], 1e-6);
        assertEquals(y, actual[1], 1e-6);
        assertEquals(z, actual[2], 1e-6);
        assertEquals(xDist, actual[3], 1e-6);
        assertEquals(yDist, actual[4], 1e-6);
        assertEquals(zDist, actual[5], 1e-6);
    }
}
