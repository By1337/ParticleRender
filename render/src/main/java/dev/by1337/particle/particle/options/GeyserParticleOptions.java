package dev.by1337.particle.particle.options;

import dev.by1337.particle.particle.ParticleOption;
import dev.by1337.particle.particle.ParticleOptionType;
import io.netty.buffer.ByteBuf;

public record GeyserParticleOptions(int waterBlocks) implements ParticleOption {

    @Override
    public void write(ByteBuf out, int version) {
        out.writeInt(waterBlocks);
    }

    @Override
    public boolean writable(int version) {
        return version >= 776;
    }

    @Override
    public ParticleOptionType getType() {
        return ParticleOptionType.GEYSER_PARTICLE_OPTIONS;
    }
}
