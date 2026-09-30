package dev.by1337.particle.particle.options;

import dev.by1337.particle.particle.ParticleOption;
import dev.by1337.particle.particle.ParticleOptionType;
import dev.by1337.particle.netty.buffer.ByteBufUtil;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record VibrationParticleOption(BlockPos origin, PositionSource destination, int arrivalInTicks)
        implements ParticleOption {

    public VibrationParticleOption {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        if (!(destination instanceof BlockSource) && !(destination instanceof EntitySource)) {
            throw new IllegalArgumentException("Unknown position source: " + destination.getClass());
        }
        if (arrivalInTicks < 0) throw new IllegalArgumentException("arrivalInTicks must be non-negative");
    }

    public static VibrationParticleOption toBlock(BlockPos origin, BlockPos destination, int arrivalInTicks) {
        return new VibrationParticleOption(origin, new BlockSource(destination), arrivalInTicks);
    }

    public static VibrationParticleOption toEntity(BlockPos origin, int entityId, float yOffset, int arrivalInTicks) {
        return new VibrationParticleOption(origin, new EntitySource(entityId, yOffset), arrivalInTicks);
    }

    @Override
    public void write(ByteBuf out, int version) {
        if (!writable(version)) throw new UnsupportedOperationException(version + " is not supported for this vibration");
        // 1.17-1.18.2 use VibrationPath: origin, destination, arrival ticks.
        if (version <= 758) writeBlockPos(out, origin);
        if (version <= 765) {
            String name = destination instanceof BlockSource ? "minecraft:block" : "minecraft:entity";
            byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
            ByteBufUtil.writeVarInt(out, bytes.length);
            out.writeBytes(bytes);
        } else {
            // The position source registry contains block (0), then entity (1).
            ByteBufUtil.writeVarInt(out, destination instanceof BlockSource ? 0 : 1);
        }
        if (destination instanceof BlockSource block) {
            writeBlockPos(out, block.pos());
        } else if (destination instanceof EntitySource entity) {
            ByteBufUtil.writeVarInt(out, entity.entityId());
            if (version >= 759) out.writeFloat(entity.yOffset());
        }
        ByteBufUtil.writeVarInt(out, arrivalInTicks);
    }

    @Override
    public boolean writable(int version) {
        return version >= 755 && version <= 777
                && (version >= 759 || !(destination instanceof EntitySource entity && entity.yOffset() != 0));
    }

    @Override
    public ParticleOptionType getType() {
        return ParticleOptionType.VIBRATION_PARTICLE_OPTION;
    }

    private static void writeBlockPos(ByteBuf out, BlockPos pos) {
        out.writeLong(((long) pos.x() & 0x3FFFFFFL) << 38
                | ((long) pos.z() & 0x3FFFFFFL) << 12
                | ((long) pos.y() & 0xFFFL));
    }

    public record BlockPos(int x, int y, int z) { }

    public interface PositionSource { }

    public record BlockSource(BlockPos pos) implements PositionSource {
        public BlockSource {
            Objects.requireNonNull(pos, "pos");
        }
    }

    public record EntitySource(int entityId, float yOffset) implements PositionSource {
        public EntitySource {
            if (entityId < 0) throw new IllegalArgumentException("entityId must be non-negative");
            if (!Float.isFinite(yOffset)) throw new IllegalArgumentException("yOffset must be finite");
        }
    }
}
