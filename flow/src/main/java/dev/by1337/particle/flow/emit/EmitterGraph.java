package dev.by1337.particle.flow.emit;

import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.particle.particle.ParticleSource;
import dev.by1337.particle.particle.PrecomputedParticleSource;
import dev.by1337.yaml.decoder.YamlDecoder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Map;
import java.util.Objects;

public final class EmitterGraph {
    public static final YamlDecoder<EmitterGraph> DECODER = YamlDecoder
            .mapOf(YamlDecoder.STRING, ParticleEmitter.DECODER)
            .map(EmitterGraph::new);

    private final Object2ObjectOpenHashMap<String, ParticleEmitter> nodes;
    private final ParticleEmitter root;
    private final BitSet executionKey = new BitSet();
    private final Object2ObjectOpenHashMap<BitSet, PrecomputedParticleSource> cachedTicks = new Object2ObjectOpenHashMap<>();
    private long cacheSize;

    public EmitterGraph(Map<String, ParticleEmitter> nodes) {
        this.nodes = new Object2ObjectOpenHashMap<>(Objects.requireNonNull(nodes, "nodes"));
        this.root = this.nodes.get("__root__");
        if (root == null) throw new IllegalArgumentException("Missing __root__ emitter");
        int x = 0;
        for (ParticleEmitter emitter : this.nodes.values()) {
            emitter.link(this, x++);
        }
    }

    public ParticleEmitter root() {
        return root;
    }

    public PrecomputedParticleSource make(int tick) {
        executionKey.clear();
        root.executionKey(tick, executionKey);
        var v = cachedTicks.get(executionKey);
        if (v != null) return v;
        v = new ParticleSource() {
            @Override
            public void doWrite(PacketBuilder writer, double baseX, double baseY, double baseZ) {
                root.emit(new EmitContext(512, tick), writer, baseX, baseY, baseZ, 0, 0, 0);
            }
        }.compute();
        cacheSize += v.sizeBytes();
        cachedTicks.put((BitSet)executionKey.clone(), v);
        return v;
    }

    public @Nullable ParticleEmitter find(String name) {
        return nodes.get(name);
    }

    @ApiStatus.Internal
    public Object2ObjectOpenHashMap<BitSet, PrecomputedParticleSource> cachedTicks() {
        return cachedTicks;
    }

    @ApiStatus.Internal
    public long cacheSize() {
        return cacheSize;
    }
}
