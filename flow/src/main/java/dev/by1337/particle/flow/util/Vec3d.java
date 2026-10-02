package dev.by1337.particle.flow.util;

import dev.by1337.yaml.decoder.InlineYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

public record Vec3d(double x, double y, double z) {
    public static final Vec3d ZERO = new Vec3d(0, 0, 0);
    public static final YamlDecoder<Vec3d> DECODER = InlineYamlDecoder.inline(
            ";",
            "<x>;<y>;<z>",
            Vec3d::new,
            YamlDecoder.DOUBLE.fieldOf(),
            YamlDecoder.DOUBLE.fieldOf(),
            YamlDecoder.DOUBLE.fieldOf()
    );
}
