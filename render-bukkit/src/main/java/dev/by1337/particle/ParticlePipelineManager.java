package dev.by1337.particle;

import dev.by1337.particle.netty.handler.ParticleEncoder;
import dev.by1337.particle.util.netty.ChannelUtil;
import dev.by1337.particle.util.Version;
import io.netty.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.Closeable;

class ParticlePipelineManager implements Listener, Closeable {
    private final String handlerName;

    public ParticlePipelineManager(Plugin plugin, String handlerName) {
        this.handlerName = handlerName;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getOnlinePlayers().forEach(this::hook);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        hook(event.getPlayer());
    }

    private void hook(Player player) {
        Channel channel = ChannelUtil.getChannel(player);
        channel.pipeline().addBefore("prepender", handlerName, new ParticleEncoder(channel, player.getUniqueId(), Version.VERSION.protocolVersion()));
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        Bukkit.getOnlinePlayers().forEach(player -> {
            Channel channel = ChannelUtil.getChannel(player);
            channel.pipeline().remove(handlerName);
        });
    }
}
