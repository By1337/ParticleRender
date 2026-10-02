package dev.by1337.particle.flow.emit;

import dev.by1337.yaml.codec.DataResult;
import dev.by1337.yaml.decoder.YamlDecoder;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps a YAML {@code type} to an emitter decoder.
 */
public final class ParticleEmitterRegistry {
    public static final ParticleEmitterRegistry DEFAULT = new ParticleEmitterRegistry()
            .register("base:root", RootParticleEmitter.DECODER)
            .register("base:particle", SimpleParticleEmitter.DECODER)
            .register("base:circle", CircleParticleEmitter.DECODER)
            .register("base:sphere", SphereParticleEmitter.DECODER)
            .register("base:line", LineParticleEmitter.DECODER)
            .register("base:rectangle", RectangleParticleEmitter.DECODER)
            .register("base:box_surface", BoxSurfaceParticleEmitter.DECODER);

    private final Map<String, YamlDecoder<? extends ParticleEmitter>> decoders = new ConcurrentHashMap<>();
    private final YamlDecoder<ParticleEmitter> decoder = YamlDecoder.YAML_MAP.flatMap((ctx, map) -> {
        if (!map.has("type")) return DataResult.error("Missing emitter type");
        return YamlDecoder.STRING.decode(ctx, map.get("type")).flatMap(type -> {
            YamlDecoder<? extends ParticleEmitter> selected = decoders.get(type);
            if (selected == null) return DataResult.error("Unknown emitter type: " + type);
            return selected.decode(ctx, map.get()).widen();
        });
    });

    public ParticleEmitterRegistry register(String type, YamlDecoder<? extends ParticleEmitter> decoder) {
        Objects.requireNonNull(type, "type");
        if (type.isBlank()) throw new IllegalArgumentException("Emitter type must not be blank");
        Objects.requireNonNull(decoder, "decoder");
        if (decoders.putIfAbsent(type, decoder) != null) {
            throw new IllegalArgumentException("Duplicate emitter type: " + type);
        }
        return this;
    }

    public YamlDecoder<ParticleEmitter> decoder() {
        return decoder;
    }

    public Set<String> types() {
        return Set.copyOf(decoders.keySet());
    }
}
