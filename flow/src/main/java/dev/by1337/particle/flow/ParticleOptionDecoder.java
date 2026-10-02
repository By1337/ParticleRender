package dev.by1337.particle.flow;

import dev.by1337.particle.BlockType;
import dev.by1337.particle.ItemType;
import dev.by1337.particle.particle.ParticleOption;
import dev.by1337.particle.particle.options.*;
import dev.by1337.particle.particle.options.VibrationParticleOption.BlockPos;
import dev.by1337.yaml.codec.DataResult;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ParticleOptionDecoder {
    private ParticleOptionDecoder() {
    }

    private static final YamlDecoder<Integer> RGB = YamlDecoder.STRING.flatMap(s -> color(s, 6));
    private static final YamlDecoder<Integer> ARGB = YamlDecoder.STRING.flatMap(s -> color(s, 8));
    private static final YamlDecoder<BlockPos> BLOCK_POS = YamlDecoder.STRING.flatMap(s -> {
        String[] parts = s.split(";", -1);
        if (parts.length != 3) return DataResult.error("Expected block position x;y;z: " + s);
        try {
            return DataResult.success(new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())));
        } catch (NumberFormatException e) {
            return DataResult.error("Expected integer block position x;y;z: " + s);
        }
    });
    private static final YamlDecoder<double[]> POSITION = YamlDecoder.STRING.flatMap(s -> {
        String[] parts = s.split(";", -1);
        if (parts.length != 3) return DataResult.error("Expected position x;y;z: " + s);
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return DataResult.error("Position coordinates must be finite: " + s);
            }
            return DataResult.success(new double[]{x, y, z});
        } catch (NumberFormatException e) {
            return DataResult.error("Expected numeric position x;y;z: " + s);
        }
    });

    public static final YamlDecoder<BlockParticleOption> BLOCK = RecordYamlDecoder.mapOf(
            block -> new BlockParticleOption(Objects.requireNonNull(block, "block is required")),
            YamlDecoder.STRING.flatMap(ParticleOptionDecoder::lookupBlock).fieldOf("block"));
    public static final YamlDecoder<ColorParticleOption> COLOR = RecordYamlDecoder.mapOf(
            ColorParticleOption::new, ARGB.fieldOf("argb"));
    public static final YamlDecoder<DustColorTransitionOptions> DUST_COLOR_TRANSITION = RecordYamlDecoder.mapOf(
            DustColorTransitionOptions::new,
            RGB.fieldOf("rgbFrom"), RGB.fieldOf("rgbTo"), YamlDecoder.FLOAT.fieldOf("size"));
    public static final YamlDecoder<DustParticleOptions> DUST = RecordYamlDecoder.mapOf(
            DustParticleOptions::new, RGB.fieldOf("rgb"), YamlDecoder.FLOAT.fieldOf("size"));
    public static final YamlDecoder<GeyserBaseParticleOptions> GEYSER_BASE = RecordYamlDecoder.mapOf(
            GeyserBaseParticleOptions::new,
            YamlDecoder.INT.fieldOf("waterBlocks"), YamlDecoder.FLOAT.fieldOf("burstImpulseBase"));
    public static final YamlDecoder<GeyserParticleOptions> GEYSER = RecordYamlDecoder.mapOf(
            GeyserParticleOptions::new, YamlDecoder.INT.fieldOf("waterBlocks"));
    public static final YamlDecoder<ItemParticleOption> ITEM = RecordYamlDecoder.mapOf(
            item -> new ItemParticleOption(Objects.requireNonNull(item, "item is required")),
            YamlDecoder.STRING.flatMap(ParticleOptionDecoder::lookupItem).fieldOf("item"));
    public static final YamlDecoder<PowerParticleOption> POWER = RecordYamlDecoder.mapOf(
            PowerParticleOption::new, YamlDecoder.FLOAT.fieldOf("power"));
    public static final YamlDecoder<SculkChargeParticleOptions> SCULK_CHARGE = RecordYamlDecoder.mapOf(
            SculkChargeParticleOptions::new, YamlDecoder.FLOAT.fieldOf("roll"));
    public static final YamlDecoder<ShriekParticleOption> SHRIEK = RecordYamlDecoder.mapOf(
            ShriekParticleOption::new, YamlDecoder.INT.fieldOf("delay"));
    public static final YamlDecoder<SpellParticleOption> SPELL = RecordYamlDecoder.mapOf(
            SpellParticleOption::new, ARGB.fieldOf("argb"), YamlDecoder.FLOAT.fieldOf("power"));
    public static final YamlDecoder<TrailParticleOption> TRAIL = RecordYamlDecoder.mapOf(
            (double[] pos, Integer color, Integer duration) ->
                    new TrailParticleOption(pos[0], pos[1], pos[2], color, duration),
            POSITION.fieldOf("pos"), RGB.fieldOf("color"), YamlDecoder.INT.fieldOf("duration"));
    public static final YamlDecoder<VibrationParticleOption> VIBRATION = RecordYamlDecoder.mapOf(
            VibrationParticleOption::toBlock,
            BLOCK_POS.fieldOf("origin"), BLOCK_POS.fieldOf("destination"),
            YamlDecoder.INT.fieldOf("arrivalInTicks"));

    private static final Map<Set<String>, YamlDecoder<? extends ParticleOption>> OPTIONS = options();

    public static final YamlDecoder<ParticleOption> DECODER = YamlDecoder.YAML_MAP.flatMap((ctx, map) -> {
        YamlDecoder<? extends ParticleOption> decoder = OPTIONS.get(map.keySet());
        if (decoder == null) return DataResult.error("Unknown particle option fields: " + map.keySet());
        return decoder.decode(ctx, map.get()).widen();
    });

    private static Map<Set<String>, YamlDecoder<? extends ParticleOption>> options() {
        Map<Set<String>, YamlDecoder<? extends ParticleOption>> result = new LinkedHashMap<>();
        result.put(Set.of("block"), BLOCK);
        result.put(Set.of("argb"), COLOR);
        result.put(Set.of("rgbFrom", "rgbTo", "size"), DUST_COLOR_TRANSITION);
        result.put(Set.of("rgb", "size"), DUST);
        result.put(Set.of("waterBlocks", "burstImpulseBase"), GEYSER_BASE);
        result.put(Set.of("waterBlocks"), GEYSER);
        result.put(Set.of("item"), ITEM);
        result.put(Set.of("power"), POWER);
        result.put(Set.of("roll"), SCULK_CHARGE);
        result.put(Set.of("delay"), SHRIEK);
        result.put(Set.of("argb", "power"), SPELL);
        result.put(Set.of("pos", "color", "duration"), TRAIL);
        result.put(Set.of("origin", "destination", "arrivalInTicks"), VIBRATION);
        return Map.copyOf(result);
    }

    private static DataResult<Integer> color(String s, int digits) {
        if (s.length() != digits + 1 || s.charAt(0) != '#') {
            return DataResult.error("Expected #" + (digits == 6 ? "rrggbb" : "aarrggbb") + ": " + s);
        }
        try {
            return DataResult.success((int) Long.parseLong(s.substring(1), 16));
        } catch (NumberFormatException e) {
            return DataResult.error("Invalid hex color: " + s);
        }
    }

    private static DataResult<BlockType> lookupBlock(String name) {
        String id = name.contains(":") ? name : "minecraft:" + name;
        BlockType block = BlockType.getById(id);
        return block == null ? DataResult.error("Unknown block: " + name) : DataResult.success(block);
    }

    private static DataResult<ItemType> lookupItem(String name) {
        String id = name.contains(":") ? name : "minecraft:" + name;
        ItemType item = ItemType.getById(id);
        return item == null ? DataResult.error("Unknown item: " + name) : DataResult.success(item);
    }
}
