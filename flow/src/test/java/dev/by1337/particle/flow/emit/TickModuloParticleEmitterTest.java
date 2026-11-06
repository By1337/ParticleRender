package dev.by1337.particle.flow.emit;

import dev.by1337.particle.flow.connection.ConnectionCondition;
import dev.by1337.particle.flow.connection.Connections;
import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.yaml.YamlMap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TickModuloParticleEmitterTest {
    @Test
    public void wrapsInclusiveBoundsAndRestoresContextEvenOnFailure() {
        List<Integer> ticks = new ArrayList<>();
        ParticleEmitter target = new ParticleEmitter() {
            public void link(EmitterGraph graph, int id) {
            }

            public void executionKey(int tick, BitSet set) {
            }

            public void emit(EmitContext ctx, PacketBuilder out, double x, double y, double z,
                float dx, float dy, float dz) {
                ticks.add(ctx.tick());
                assertEquals(1, x, 0);
                assertEquals(6, dz, 0);
                if (ctx.tick() == 20) throw new IllegalStateException("test");
            }
        };
        TickModuloParticleEmitter loop = new TickModuloParticleEmitter(0, 200,
                new Connections(Map.of("target", ConnectionCondition.parse(""))));
        EmitterGraph graph = new EmitterGraph(Map.of("__root__", loop, "target", target));
        for (int tick : new int[]{ 0, 200, 201, 221, 402, Integer.MAX_VALUE }) {
            EmitContext context = new EmitContext(10, tick);
            try {
                graph.root().emit(context, null, 1, 2, 3, 4, 5, 6);
            } catch (IllegalStateException expected) {
                assertEquals(221, tick);
            }
            assertEquals(tick, context.tick());
        }
        assertEquals(List.of(0, 200, 0, 20, 0, 49), ticks);
        for (int[] range : new int[][]{ { 10, 30, 31, 10 }, { 20, 20, 100, 20 }, { 0, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE } }) {
            TickModuloParticleEmitter ranged = new TickModuloParticleEmitter(range[0], range[1],
                    new Connections(Map.of("target", ConnectionCondition.parse("=" + range[3]))));
            new EmitterGraph(Map.of("__root__", ranged, "target", target));
            int before = ticks.size();
            try {
                ranged.emit(new EmitContext(10, range[2]), null, 1, 2, 3, 4, 5, 6);
            } catch (IllegalStateException expected) {
                assertEquals(20, range[3]);
            }
            assertEquals(before + 1, ticks.size());
            assertEquals(range[3], (int) ticks.get(before));
        }
        assertThrows(IllegalArgumentException.class, () -> new TickModuloParticleEmitter(-1, 200, new Connections(Map.of())));
        assertThrows(IllegalArgumentException.class, () -> new TickModuloParticleEmitter(10, 9, new Connections(Map.of())));
    }

    @Test
    public void nestedLoopsAndSiblingConditionsUseTheirOwnTicksInEmissionAndCache() {
        YamlMap yaml = new YamlMap();
        yaml.set("__root__", Map.of("type", "base:root", "connections", Map.of("loop", "", "sibling", "=221")));
        yaml.set("loop", Map.of("type", "base:tick_modulo", "max", 200,
                "connections", Map.of("nested", "=20")));
        yaml.set("nested", Map.of("type", "base:tick_modulo", "max", 9,
                "connections", Map.of("burst", "=0")));
        yaml.set("burst", Map.of("type", "base:particle", "particle", "flame", "count", 1));
        yaml.set("sibling", Map.of("type", "base:particle", "particle", "flame", "count", 1));
        EmitterGraph graph = EmitterGraph.DECODER.decode(yaml.get()).getOrThrow();
        int[] writes = { 0 };
        graph.root().emit(new EmitContext(10, 221), (particle, x, y, z, dx, dy, dz) -> writes[0]++, 0, 0, 0, 0, 0, 0);
        assertEquals(2, writes[0]);
        assertSame(graph.make(20), graph.make(422));
        assertNotSame(graph.make(20), graph.make(221));
        assertNotSame(graph.make(20), graph.make(21));
    }

    @Test
    public void validatesYamlRanges() {
        for (Map<String, Object> fields : List.of(
                Map.<String, Object>of("min", -1, "max", 20),
                Map.<String, Object>of("min", 20, "max", 19),
                Map.<String, Object>of("min", 0))) {
            YamlMap yaml = new YamlMap();
            yaml.set("type", "base:tick_modulo");
            yaml.set("connections", Map.of());
            fields.forEach(yaml::set);
            assertTrue(ParticleEmitter.DECODER.decode(yaml.get()).hasError());
        }
    }
}
