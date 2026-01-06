package dev.by1337.particle.util.netty;

import io.netty.channel.Channel;
import org.bukkit.entity.Player;

public final class ChannelUtil {
    private static ChannelGetter channel;

    public static Channel getChannel(Player player) {
        if (channel == null) {
            channel = ChannelGetterCreator.create(player);
        }
        return channel.getChannel(player);
    }
}
