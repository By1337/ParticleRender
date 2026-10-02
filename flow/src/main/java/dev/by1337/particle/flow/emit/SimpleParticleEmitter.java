package dev.by1337.particle.flow.emit;

import dev.by1337.particle.ParticleType;
import dev.by1337.particle.flow.ParticleOptionDecoder;
import dev.by1337.particle.flow.util.Vec3d;
import dev.by1337.particle.flow.util.Vec3f;
import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.particle.particle.ParticleData;
import dev.by1337.particle.particle.ParticleOption;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import dev.by1337.yaml.decoder.k2v.LookupDecoder;
import org.jetbrains.annotations.Nullable;

public final class SimpleParticleEmitter implements ParticleEmitter{
    private static final YamlDecoder<ParticleType> PARTICLE_DECODER = LookupDecoder.fromEnum(ParticleType.values());
    public static final YamlDecoder<SimpleParticleEmitter> DECODER = RecordYamlDecoder.mapOf(
            SimpleParticleEmitter::new,
            PARTICLE_DECODER.fieldOf("particle"),
            YamlDecoder.INT.fieldOf("count", 0),
            Vec3d.DECODER.fieldOf("offsets"),
            Vec3f.DECODER.fieldOf("dist"),
            YamlDecoder.BOOL.fieldOf("overrideLimiter", false),
            YamlDecoder.BOOL.fieldOf("alwaysShow", false),
            ParticleOptionDecoder.DECODER.fieldOf("data")
    );
    private final ParticleType particle;
    private final int count;
    private final @Nullable Vec3d offsets;
    private final @Nullable Vec3f dist;
    private final boolean overrideLimiter;
    private final boolean alwaysShow;
    private final @Nullable ParticleOption data;
    private final ParticleData particleData;


    public SimpleParticleEmitter(ParticleType particle, int count, @Nullable Vec3d offsets, @Nullable Vec3f dist, boolean overrideLimiter, boolean alwaysShow, @Nullable ParticleOption data) {
        this.particle = particle;
        this.count = count;
        this.offsets = offsets;
        this.dist = dist;
        this.overrideLimiter = overrideLimiter;
        this.alwaysShow = alwaysShow;
        this.data = data;
        particleData = ParticleData.builder()
                .particle(particle)
                .count(count)
                .alwaysShow(alwaysShow)
                .overrideLimiter(overrideLimiter)
                .data(data)
                .build();
    }

    @Override
    public void emit(EmitContext ctx, PacketBuilder out, double baseX, double baseY, double baseZ, float xDist, float yDist, float zDist) {
        ctx.pushDepth();
        if (offsets != null){
            baseX += offsets.x();
            baseY += offsets.y();
            baseZ += offsets.z();
        }
        if (dist != null){
            xDist = dist.x();
            yDist = dist.y();
            zDist = dist.z();
        }
        particleData.doWrite(out, baseX, baseY, baseZ, xDist, yDist, zDist);
        ctx.popDepth();
    }

    @Override
    public void link(EmitterGraph graph) {
    }
}
