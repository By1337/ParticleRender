package dev.by1337.particle;

import dev.by1337.particle.netty.handler.ParticleEncoder;
import dev.by1337.particle.hook.BThemesColorHook;
import dev.by1337.particle.hook.ThemeColorContext;
import dev.by1337.particle.util.Version;
import dev.by1337.particle.util.netty.ChannelUtil;
import io.netty.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.ToIntFunction;

class ParticlePipelineManager implements Listener, Closeable {
    private final String handlerName;
    private final @Nullable ToIntFunction<UUID> themeColors;

    public ParticlePipelineManager(Plugin plugin, String handlerName) {
        this.handlerName = handlerName;
        Plugin host = plugin.getServer().getPluginManager().getPlugin("BLib");
        ClassLoader loader = (host == null ? plugin : host).getClass().getClassLoader();
        themeColors = BThemesColorHook.create(loader, Bukkit::getPlayer);
        if (themeColors != null) ThemeColorContext.setProvider(themeColors);
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
        if (themeColors != null) ThemeColorContext.clearProvider(themeColors);
        HandlerList.unregisterAll(this);
        Bukkit.getOnlinePlayers().forEach(player -> {
            Channel channel = ChannelUtil.getChannel(player);
            if (channel == null) return;
            var pipeline = channel.pipeline();
            if (pipeline.get(handlerName) != null) {
                try {
                    pipeline.remove(handlerName);
                } catch (NoSuchElementException ignored) {
                }
            }

        });
    }
}
