package dev.by1337.particle.util.netty;

import io.netty.channel.Channel;
import org.bukkit.entity.Player;

public final class ChannelUtil {

    public static Channel getChannel(Player player) {
        return ChannelGetter.get(player);
    }
}
