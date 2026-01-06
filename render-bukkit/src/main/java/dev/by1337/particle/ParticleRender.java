package dev.by1337.particle;

import dev.by1337.particle.particle.ParticleSource;
import dev.by1337.particle.particle.ParticlePacketBuilder;
import dev.by1337.particle.util.netty.ChannelUtil;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Main API entry point for sending particle effects to players.
 * <p>
 * This class provides static methods for sending {@link ParticleSource} patterns to one or more players.
 * All particles from a single source are batched into a single network packet per player for optimal performance.
 * <p>
 * Example usage:
 * <pre>{@code
 * ParticleSource effect = new ParticleSource() {
 *     @Override
 *     public void doWrite(ParticlePacketBuilder writer, double baseX, double baseY, double baseZ) {
 *         // Define particle pattern
 *         writer.write(particleData, baseX, baseY, baseZ);
 *     }
 * };
 *
 * // Send to single player
 * FParticle.send(player, effect, x, y, z);
 *
 * // Send to multiple players
 * FParticle.send(players, effect, x, y, z);
 *
 * // Use in Stream API
 * players.stream()
 *     .filter(Player::isOnline)
 *     .forEach(FParticle.send(effect, x, y, z));
 * }</pre>
 *
 * @see ParticleSource
 * @see ParticlePacketBuilder
 */
public class ParticleRender {

    /**
     * Sends a particle effect to a single player.
     * <p>
     * The particle pattern will be rendered at the specified world coordinates.
     * All particles from the source are batched into a single network packet.
     *
     * @param receiver the player who will see the particles
     * @param particle the particle pattern to render
     * @param x        the X world coordinate
     * @param y        the Y world coordinate
     * @param z        the Z world coordinate
     * @throws NullPointerException if receiver or particle is null
     */
    public static void render(@NotNull Player receiver, @NotNull ParticleSource particle, double x, double y, double z) {
        render(receiver, particle.shift(x, y, z));
    }

    public static void render(@NotNull Collection<Player> receivers, @NotNull ParticleSource particle) {
        receivers.forEach(p -> render(p, particle));
    }

    public static void render(@NotNull Collection<Player> receivers, @NotNull ParticleSource particle, double x, double y, double z) {
        ParticleSource shifted = particle.shift(x, y, z);
        receivers.forEach(p -> render(p, shifted));
    }

    public static void render(@NotNull Stream<Player> receivers, @NotNull ParticleSource particle, double x, double y, double z) {
        ParticleSource shifted = particle.shift(x, y, z);
        receivers.forEach(p -> render(p, shifted));
    }

    /**
     * Creates a {@link Consumer} that sends particles to a player.
     * <p>
     * This method is useful for functional programming patterns, particularly
     * with Stream API. The returned consumer can be passed to {@code forEach()}
     * or other higher-order functions.
     * <p>
     * Example:
     * <pre>{@code
     * Consumer<Player> sendEffect = FParticle.send(particle, x, y, z);
     *
     * // Use with stream
     * players.stream()
     *     .filter(Player::isOnline)
     *     .forEach(sendEffect);
     *
     * // Or directly
     * players.forEach(sendEffect);
     * }</pre>
     *
     * @param particle the particle pattern to render
     * @param x        the X world coordinate
     * @param y        the Y world coordinate
     * @param z        the Z world coordinate
     * @return a consumer that sends the particle effect to a player
     * @throws NullPointerException if particle is null
     */
    public static @NotNull Consumer<Player> render(@NotNull ParticleSource particle, double x, double y, double z) {
        var shifted = particle.shift(x, y, z);
        return p -> render(p, shifted);
    }

    public static void render(Player player, ParticleSource writer) {
        var v = ChannelUtil.getChannel(player);
        if (v != null) {
            v.writeAndFlush(writer);
        }
    }
}
