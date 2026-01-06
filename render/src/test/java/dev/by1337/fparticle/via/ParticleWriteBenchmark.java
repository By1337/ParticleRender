package dev.by1337.fparticle.via;

import dev.by1337.particle.ParticleType;
import dev.by1337.particle.netty.handler.ParticleEncoder;
import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.particle.particle.ParticleData;
import dev.by1337.particle.particle.ParticleSource;
import io.netty.buffer.Unpooled;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class ParticleWriteBenchmark {
    private ParticleEncoder encoder;
    private ParticleSource computed;

    @Setup
    public void setup() {
        System.setProperty("particle.render.protocol", "754");

        computed = circle(1024 * 10, 10, ParticleData.of(ParticleType.CLOUD)).compute();

        encoder = new ParticleEncoder(null, null, 754);
        encoder.setOut(Unpooled.buffer());
    }

    @Benchmark
    public void run() {
        computed.doWrite(encoder, 0, 0, 0);
        encoder.out().clear();
    }
    public static ParticleSource circle(int points, double radius, ParticleData particle){
        return new ParticleSource() {
            @Override
            public void doWrite(PacketBuilder writer, double cx, double cy, double cz) {
                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    double x = cx + radius * Math.cos(angle);
                    double z = cz + radius * Math.sin(angle);
                    writer.append(particle, x, cy, z);
                }
            }
        };
    }
}
