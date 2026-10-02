package dev.by1337.particle.flow;

import dev.by1337.particle.BlockType;
import dev.by1337.particle.ItemType;
import dev.by1337.particle.particle.ParticleOption;
import dev.by1337.particle.particle.options.*;
import dev.by1337.particle.particle.options.VibrationParticleOption.BlockPos;
import dev.by1337.particle.particle.options.VibrationParticleOption.BlockSource;
import dev.by1337.yaml.YamlMap;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ParticleOptionDecoderTest {
    @Test
    public void decodesEveryOptionInReference() {
        assertEquals(BlockType.STONE, ((BlockParticleOption) decode("block", "stone")).block());
        assertEquals(ItemType.DIAMOND, ((ItemParticleOption) decode("item", "minecraft:diamond")).item());
        assertEquals(0x80abcdef, ((ColorParticleOption) decode("argb", "#80abcdef")).argb());
        assertEquals(new DustColorTransitionOptions(0x123456, 0xabcdef, 2.5f),
                decode("rgbFrom", "#123456", "rgbTo", "#abcdef", "size", 2.5));
        assertEquals(new DustParticleOptions(0xabcdef, 2.5f),
                decode("rgb", "#abcdef", "size", 2.5));
        assertEquals(new GeyserBaseParticleOptions(3, 4.5f),
                decode("waterBlocks", 3, "burstImpulseBase", 4.5));
        assertEquals(new GeyserParticleOptions(3), decode("waterBlocks", 3));
        assertEquals(new PowerParticleOption(1.5f), decode("power", 1.5));
        assertEquals(new SculkChargeParticleOptions(4.5f), decode("roll", 4.5));
        assertEquals(new ShriekParticleOption(10), decode("delay", 10));
        assertEquals(new SpellParticleOption(0x80abcdef, 3.5f),
                decode("argb", "#80abcdef", "power", 3.5));
        assertEquals(new TrailParticleOption(10.5, 20, -3.25, 0xabcdef, 10),
                decode("pos", "10.5;20;-3.25", "color", "#abcdef", "duration", 10));
        VibrationParticleOption vibration = (VibrationParticleOption) decode(
                "origin", "10;20;30", "destination", "15;25;35", "arrivalInTicks", 10);
        assertEquals(new BlockPos(10, 20, 30), vibration.origin());
        assertEquals(new BlockSource(new BlockPos(15, 25, 35)), vibration.destination());
    }

    @Test
    public void rejectsUnknownAndInvalidFieldSets() {
        assertTrue(option().hasError());
        assertTrue(option("unsupported", 1).hasError());
        assertTrue(option("power", 1, "delay", 1).hasError());
        assertTrue(option("rgb", "#ffffff").hasError());
        assertTrue(option("argb", "#xyzxxxxx").hasError());
        assertTrue(option("block", "not_a_block").hasError());
        assertTrue(option("origin", "1;2;3",
                "destination", Map.of("entityId", 4), "arrivalInTicks", 1).hasError());
    }

    private static ParticleOption decode(Object... fields) {
        var result = option(fields);
        assertFalse(result.error(), result.hasError());
        return result.getOrThrow();
    }

    private static dev.by1337.yaml.codec.DataResult<ParticleOption> option(Object... fields) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i += 2) values.put((String) fields[i], fields[i + 1]);
        YamlMap node = new YamlMap();
        node.set("data", values);
        return ParticleOptionDecoder.DECODER.decode(node.get("data"));
    }
}
