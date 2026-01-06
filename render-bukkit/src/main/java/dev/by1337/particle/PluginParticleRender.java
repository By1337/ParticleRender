package dev.by1337.particle;

import dev.by1337.particle.particle.PacketBuilder;
import dev.by1337.particle.particle.ParticleData;
import dev.by1337.particle.particle.ParticleSource;
import dev.by1337.particle.particle.options.BlockParticleOption;
import dev.by1337.particle.particle.options.DustParticleOptions;
import dev.by1337.particle.particle.options.ItemParticleOption;
import dev.by1337.particle.util.Version;
import dev.by1337.particle.util.netty.ChannelUtil;
import io.netty.channel.Channel;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

@ApiStatus.Internal
public class PluginParticleRender extends JavaPlugin {
    private ParticleRenderBootstrapper bootstrapper;

    private static final ParticleSource SPHERE = new ParticleSource() {
        private final Random random = new Random();
        private final ParticleData particle = ParticleData.builder()
                .maxSpeed(0.2f)
                .data(new DustParticleOptions(Color.AQUA.asRGB(), 1.f))
                .particle(ParticleType.DUST)
                .build();

        @Override
        public void doWrite(PacketBuilder writer, double baseX, double baseY, double baseZ) {
            final double radius = 5;
            for (int i = 0; i < 256; i++) {
                double phi = random.nextDouble() * Math.PI * 2;
                double theta = Math.acos(2 * random.nextDouble() - 1);

                double sinTheta = Math.sin(theta);

                double x = radius * sinTheta * Math.cos(phi);
                double y = radius * sinTheta * Math.sin(phi);
                double z = radius * Math.cos(theta);

                float offsetX = (float) (-x / radius);
                float offsetY = (float) (-y / radius);
                float offsetZ = (float) (-z / radius);
                writer.append(particle,
                        x + baseX,
                        y + baseY,
                        z + baseZ
                        , offsetX, offsetY, offsetZ);
            }
        }
    };

    private static final ParticleSource TEST = new ParticleSource() {
        private final Random random = new Random();
        private final ParticleData particle = ParticleData.builder()
                .maxSpeed(0f)
                .particle(ParticleType.FIREWORK)
                .count(50)
                .build();

        @Override
        public void doWrite(PacketBuilder writer, double baseX, double baseY, double baseZ) {
            writer.append(
                    particle,
                    baseX,
                    baseY,
                    baseZ,
                    10,
                    10,
                    10
            );
        }
    };
    private static final ParticleSource TEST_CIRCLE = circle(256, 6, ParticleData.of(ParticleType.FLAME)).compute();


    @Override
    public void onLoad() {
        int ignored = ItemType.BARRIER.getProtocolId(Version.VERSION.protocolVersion()); //preload
    }

    @Override
    public void onEnable() {
        bootstrapper = new ParticleRenderBootstrapper("particleRender", this);
        bootstrapper.enable();
    }

    @Override
    public void onDisable() {
        bootstrapper.disable();
    }


    // @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = (Player) sender;
        if (args.length == 1 && args[0].equals("all")) {
            new BukkitRunnable() {
                final Iterator<ParticleType> iterator = ParticleType.iterator();

                @Override
                public void run() {
                    if (!iterator.hasNext()) {
                        cancel();
                        return;
                    }
                    ParticleType type = iterator.next();
                    var loc = player.getLocation();
                    player.sendMessage(Component.text(type.id()));
                    ParticleRender.render(player, ParticleData.of(type), loc.getX(), loc.getY(), loc.getZ());
                }
            }.runTaskTimerAsynchronously(PluginParticleRender.this, 0, 10);
        } else if (args.length == 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    sendShifted(player, TEST_CIRCLE);
                }
            }.runTaskTimerAsynchronously(PluginParticleRender.this, 0, 1);
        } else {
            try {
                ParticleType type = ParticleType.byId(args[0]);
                var loc = player.getLocation();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Channel c = ChannelUtil.getChannel(player);
                        var b = ParticleData.builder().yDist(1);
                        if (args.length == 2 && type == ParticleType.ITEM) {
                            ItemType itemType = ItemType.getById(args[1]);
                            if (itemType != null) {
                                b.data(new ItemParticleOption(itemType));
                            }
                        } else if (args.length == 2) {
                            BlockType blockType = BlockType.getById(args[1]);
                            if (blockType != null) {
                                b.data(new BlockParticleOption(blockType));
                            }
                        }
                        try {
                            c.writeAndFlush(b.particle(type).build().shift(loc.getX(), loc.getY(), loc.getZ()))
                                    .sync();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }.runTaskAsynchronously(this);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("Invalid particle type!");
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Stream.of(ParticleType.values())
                    .map(ParticleType::id)
                    .filter(s -> s.toLowerCase().contains(args[0].toLowerCase()))
                    .toList();
        } else if (args.length == 2 && args[0].contains("item")) {
            return Stream.of(ItemType.values())
                    .map(ItemType::id)
                    .filter(s -> s.toLowerCase().contains(args[1].toLowerCase()))
                    .toList();
        } else if (args.length == 2) {
            return Stream.of(BlockType.values())
                    .map(BlockType::id)
                    .filter(s -> s.toLowerCase().contains(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }


    public static void sendShifted(Player player, ParticleSource particleSource) {
        var loc = player.getLocation();
        ParticleRender.render(player, particleSource, loc.getX(), loc.getY(), loc.getZ());
    }

    public static ParticleSource circle(int points, double radius, ParticleData particle) {
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