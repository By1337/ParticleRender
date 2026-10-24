package dev.by1337.particle.particle.options;

import dev.by1337.particle.hook.ThemeColorContext;
import dev.by1337.particle.particle.ParticleOption;
import dev.by1337.particle.particle.ParticleOptionType;
import io.netty.buffer.ByteBuf;

/** Dust whose RGB is read from the encoder's per-viewer snapshot, never baked into the source. */
public record ThemedDustParticleOptions(float size) implements ParticleOption {
    public static final ThemedDustParticleOptions DEFAULT = new ThemedDustParticleOptions(1f);

    public static ThemedDustParticleOptions from(ParticleOption option) {
        if (option instanceof ThemedDustParticleOptions themed) return themed;
        if (option instanceof DustParticleOptions dust) return new ThemedDustParticleOptions(dust.size());
        return DEFAULT;
    }

    @Override
    public void write(ByteBuf out, int version) {
        DustParticleOptions.write(out, version, ThemeColorContext.current(), size);
    }

    @Override
    public boolean writable(int version) { return true; }

    @Override
    public ParticleOptionType getType() { return ParticleOptionType.DUST_PARTICLE_OPTIONS; }
}
