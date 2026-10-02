package dev.by1337.particle.flow.emit;

import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.yaml.YamlMap;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.junit.Test;

import java.util.BitSet;
import java.util.Map;

import static org.junit.Assert.*;

public class ParticleEmitterRegistryTest {
    @Test
    public void dispatchesEveryBuiltInType() {
        assertInstance("base:root", RootParticleEmitter.class);
        assertInstance("base:particle", SimpleParticleEmitter.class,
                "particle", "dust", "count", 1,
                "data", Map.of("rgb", "#ffffff", "size", 1.0));
        assertInstance("base:circle", CircleParticleEmitter.class, "points", 4, "radius", 2);
        assertInstance("base:sphere", SphereParticleEmitter.class, "points", 8, "radius", 2);
        assertInstance("base:line", LineParticleEmitter.class, "points", 2, "length", 4);
        assertInstance("base:rectangle", RectangleParticleEmitter.class,
                "points", 4, "width", 4, "height", 2);
        assertInstance("base:box_surface", BoxSurfaceParticleEmitter.class,
                "points", 6, "size", "2;2;2");
    }

    @Test
    public void rejectsMissingUnknownAndDuplicateTypes() {
        YamlMap node = new YamlMap();
        assertTrue(ParticleEmitter.DECODER.decode(node.get()).hasError());
        node.set("type", "base:unknown");
        assertTrue(ParticleEmitter.DECODER.decode(node.get()).hasError());
        node.set("type", "base:circle");
        node.set("points", 0);
        node.set("radius", 2);
        node.set("connections", Map.of());
        assertTrue(ParticleEmitter.DECODER.decode(node.get()).hasError());

        ParticleEmitterRegistry registry = new ParticleEmitterRegistry();
        registry.register("custom:test", (ctx, yaml) ->
                YamlDecoder.YAML_MAP.decode(ctx, yaml).map(map -> new RootParticleEmitter(
                        new dev.by1337.particle.flow.connection.Connections(Map.of()))));
        assertEquals(RootParticleEmitter.class,
                registry.decoder().decode(node("custom:test").get()).getOrThrow().getClass());
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("custom:test", RootParticleEmitter.DECODER));
    }

    @Test
    public void rootLinksConnectionsAndPassesThroughIncomingValues() {
        YamlMap root = node("base:root");
        root.set("connections", Map.of("target", "", "blocked", "<0"));
        RootParticleEmitter emitter = (RootParticleEmitter) ParticleEmitter.DECODER.decode(root.get()).getOrThrow();
        final int[] calls = {0};
        ParticleEmitter target = new ParticleEmitter() {
            @Override
            public void emit(EmitContext ctx, PacketBuilder out, double x, double y, double z,
                             float dx, float dy, float dz) {
                calls[0]++;
                assertEquals(1, x, 0);
                assertEquals(2, y, 0);
                assertEquals(3, z, 0);
                assertEquals(4, dx, 0);
                assertEquals(5, dy, 0);
                assertEquals(6, dz, 0);
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
        emitter.link(graph, 0);
        PacketBuilder out = (particle, x, y, z, dx, dy, dz) -> { };
        emitter.emit(new EmitContext(2, 0), out, 1, 2, 3, 4, 5, 6);
        assertEquals(1, calls[0]);
    }

    private static void assertInstance(String type, Class<? extends ParticleEmitter> expected, Object... fields) {
        YamlMap node = node(type);
        node.set("connections", Map.of());
        for (int i = 0; i < fields.length; i += 2) node.set((String) fields[i], fields[i + 1]);
        var result = ParticleEmitter.DECODER.decode(node.get());
        assertFalse(result.error(), result.hasError());
        assertEquals(expected, result.getOrThrow().getClass());
    }

    private static YamlMap node(String type) {
        YamlMap node = new YamlMap();
        node.set("type", type);
        return node;
    }
}
