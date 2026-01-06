package dev.by1337.particle.util.reflect;

import io.netty.channel.Channel;
import org.bukkit.entity.Player;

public interface ChannelGetter {
    Channel getChannel(Player pl);
}
