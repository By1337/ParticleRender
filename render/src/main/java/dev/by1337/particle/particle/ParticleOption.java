package dev.by1337.particle.particle;

import io.netty.buffer.ByteBuf;

public interface ParticleOption {
    void write(ByteBuf out, int version);
    boolean writable(int version);
    ParticleOptionType getType();
}
