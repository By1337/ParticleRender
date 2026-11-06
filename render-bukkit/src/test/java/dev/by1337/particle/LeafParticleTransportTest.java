package dev.by1337.particle;

import dev.by1337.leaf.api.network.PacketSink;
import dev.by1337.particle.particle.ParticleData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.URLClassLoader;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class LeafParticleTransportTest {
    @Test
    public void inactiveChannelKeepsOrdinaryPaperPath() {
        var channel = new EmbeddedChannel();
        try {
            assertFalse(LeafParticleTransport.active(channel));
            assertFalse(LeafParticleTransport.send(channel, UUID.randomUUID(), ParticleData.of(ParticleType.FLAME), false));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void delegatesToConnectionSinkAndPreservesExplicitFlush() {
        var channel = new EmbeddedChannel();
        try {
            AtomicBoolean called = new AtomicBoolean();
            channel.attr(PacketSink.KEY).set((source, flush) -> {
                assertTrue(flush);
                assertNotNull(source);
                called.set(true);
                return true;
            });
            assertTrue(LeafParticleTransport.active(channel));
            assertTrue(LeafParticleTransport.send(channel, UUID.randomUUID(), ParticleData.of(ParticleType.FLAME), true));
            assertTrue(called.get());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void adapterLoadsWhenLeafApiIsCompletelyUnavailable() throws Exception {
        var url = LeafParticleTransport.class.getProtectionDomain().getCodeSource().getLocation();
        var channel = new EmbeddedChannel();
        try (var loader = new URLClassLoader(new java.net.URL[]{ url }, getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("dev.by1337.leaf.api.")) throw new ClassNotFoundException(name);
                if (name.startsWith("dev.by1337.particle.LeafParticleTransport")) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = findClass(name);
                    if (resolve) resolveClass(type);
                    return type;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            var type = loader.loadClass("dev.by1337.particle.LeafParticleTransport");
            var method = type.getDeclaredMethod("active", io.netty.channel.Channel.class);
            method.setAccessible(true);
            assertEquals(false, method.invoke(null, channel));
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
