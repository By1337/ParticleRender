package dev.by1337.particle.flow.util;

import dev.by1337.yaml.decoder.InlineYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

public record Vec3f(float x, float y, float z) {
    public static final Vec3f ZERO = new Vec3f(0, 0, 0);
    public static final YamlDecoder<Vec3f> DECODER = InlineYamlDecoder.inline(
            ";",
            "<x>;<y>;<z>",
            Vec3f::new,
            YamlDecoder.FLOAT.fieldOf(),
            YamlDecoder.FLOAT.fieldOf(),
            YamlDecoder.FLOAT.fieldOf()
    );
}
