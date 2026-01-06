package dev.by1337.particle;

import dev.by1337.particle.util.reflect.ChannelGetter;
import dev.by1337.particle.util.reflect.ChannelGetterCreator;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;

public final class FParticleUtil {
    private static ChannelGetter channel;

    public static Channel getChannel(Player player) {
        if (channel == null) {
            channel = ChannelGetterCreator.create(player);
        }
        return channel.getChannel(player);
    }
}
