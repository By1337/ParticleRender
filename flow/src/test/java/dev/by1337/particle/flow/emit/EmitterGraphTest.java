package dev.by1337.particle.flow.emit;

import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.yaml.YamlMap;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class EmitterGraphTest {
    @Test
    public void linksAfterAllNodesAreAvailable() {
        final int[] links = {0};
        ParticleEmitter later = new ParticleEmitter() {
            @Override
            public void emit(EmitContext ctx, PacketBuilder out, double x, double y, double z,
                             float dx, float dy, float dz) { }

            @Override
            public void link(EmitterGraph graph) {
                links[0]++;
            }
        };
        ParticleEmitter root = new ParticleEmitter() {
            @Override
            public void emit(EmitContext ctx, PacketBuilder out, double x, double y, double z,
                             float dx, float dy, float dz) { }

            @Override
            public void link(EmitterGraph graph) {
                assertSame(later, graph.find("later"));
                links[0]++;
            }
        };

        EmitterGraph graph = new EmitterGraph(Map.of("__root__", root, "later", later));
        assertSame(root, graph.root());
        assertSame(later, graph.find("later"));
        assertNull(graph.find("missing"));
        assertEquals(2, links[0]);
        assertThrows(IllegalArgumentException.class, () -> new EmitterGraph(Map.of("later", later)));
    }

    @Test
    public void decodesTopLevelMapAndLinksRootToParticle() {
        YamlMap yaml = new YamlMap();
        yaml.set("__root__", Map.of(
                "type", "base:root",
                "connections", Map.of("particle", "")));
        yaml.set("particle", Map.of(
                "type", "base:particle",
                "particle", "dust",
                "count", 1,
                "data", Map.of("rgb", "#ffffff", "size", 1.0)));

        var decoded = EmitterGraph.DECODER.decode(yaml.get());
        assertFalse(decoded.error(), decoded.hasError());
        EmitterGraph graph = decoded.getOrThrow();
        assertTrue(graph.root() instanceof RootParticleEmitter);
        assertTrue(graph.find("particle") instanceof SimpleParticleEmitter);
        final int[] writes = {0};
        PacketBuilder out = (particle, x, y, z, dx, dy, dz) -> writes[0]++;
        graph.root().emit(new EmitContext(3), out, 1, 2, 3, 0, 0, 0);
        assertEquals(1, writes[0]);

        YamlMap missingRoot = new YamlMap();
        missingRoot.set("particle", yaml.getRaw("particle"));
        assertTrue(EmitterGraph.DECODER.decode(missingRoot.get()).hasError());
    }
}
