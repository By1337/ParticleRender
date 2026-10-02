package dev.by1337.particle.flow.emit;

import dev.by1337.yaml.decoder.YamlDecoder;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public final class EmitterGraph {
    public static final YamlDecoder<EmitterGraph> DECODER = YamlDecoder
            .mapOf(YamlDecoder.STRING, ParticleEmitter.DECODER)
            .map(EmitterGraph::new);

    private final Map<String, ParticleEmitter> nodes;
    private final ParticleEmitter root;

    public EmitterGraph(Map<String, ParticleEmitter> nodes) {
        this.nodes = Map.copyOf(Objects.requireNonNull(nodes, "nodes"));
        this.root = this.nodes.get("__root__");
        if (root == null) throw new IllegalArgumentException("Missing __root__ emitter");
        this.nodes.values().forEach(emitter -> emitter.link(this));
    }

    public ParticleEmitter root() {
        return root;
    }

    public @Nullable ParticleEmitter find(String name) {
        return nodes.get(name);
    }
}
