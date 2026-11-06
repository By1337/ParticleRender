package dev.by1337.particle;

import dev.by1337.particle.particle.ParticleSource;
import io.netty.channel.Channel;
import java.util.UUID;

/** Optional integration: ordinary Paper never needs to resolve the Leaf API classes. */
final class LeafParticleTransport {
    private static final boolean AVAILABLE = available();

    private static boolean available() {
        try {
            Class.forName("dev.by1337.leaf.api.network.PacketSink", false, LeafParticleTransport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
    }

    static boolean active(Channel channel) {
        return AVAILABLE && Adapter.active(channel);
    }

    static boolean send(Channel channel, UUID player, ParticleSource source, boolean flush) {
        return AVAILABLE && Adapter.send(channel, player, source, flush);
    }

    private static final class Adapter {
        static boolean active(Channel channel) {
            return channel.attr(dev.by1337.leaf.api.network.PacketSink.KEY).get() != null;
        }

        static boolean send(Channel channel, UUID player, ParticleSource source, boolean flush) {
            var sink = channel.attr(dev.by1337.leaf.api.network.PacketSink.KEY).get();
            return sink != null && sink.submit(output -> dev.by1337.particle.netty.handler.IntegratedParticleEncoder.write(source, output, player), flush);
        }
    }
}
