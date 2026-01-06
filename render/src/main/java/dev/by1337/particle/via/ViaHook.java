package dev.by1337.particle.via;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.exception.CancelEncoderException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * A utility class that interacts with the ViaVersion API to manage protocol transformations
 * for players on specific channels.
 * <p>
 * This class determines if the ViaVersion library is available and provides functionality
 * to mutate network packets based on protocol versions. If the library is unavailable, it
 * defaults to native behavior without packet transformations.
 */
public final class ViaHook {
    private static final boolean HAS_VIA;

    public static ViaMutator getViaMutator(UUID player, Channel channel, int protocolVersion) {
        if (!HAS_VIA) return ViaMutator.of(protocolVersion);
        UserConnection connection = Via.getAPI().getConnection(player);
        if (connection == null) return ViaMutator.of(protocolVersion);
        if (!connection.shouldTransformPacket()) return ViaMutator.of(protocolVersion);

        return new ViaMutator(
                connection.getProtocolInfo().protocolVersion().getVersion(),
                b -> {
                    if (channel.isOpen()) connection.transformClientbound(b, CancelEncoderException::generate);
                }
        );
    }

    static {
        boolean hasVia;
        try {
            Class.forName("com.viaversion.viaversion.api.Via");
            hasVia = true;
        } catch (Exception | ClassFormatError e) {
            hasVia = false;
        }
        HAS_VIA = hasVia;
    }

    public record ViaMutator(int protocol, Consumer<ByteBuf> mutator) {
        public static ViaMutator of(int protocol) {
            return new ViaMutator(protocol, b -> {});
        }
    }
}
