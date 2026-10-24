package dev.by1337.particle.particle;

import org.jetbrains.annotations.Contract;

/**
 * Reusable particle pattern, composable with transforms and other sources.
 * Implement {@link #doWrite(PacketBuilder, double, double, double)} using
 * {@link PacketBuilder#append(ParticleData, double, double, double)}.
 * Bukkit hosts send it with {@code ParticleRender.render(player, source, x, y, z)}.
 * <p>
 * Encoding callbacks may run on a channel thread: capture world state before sending.
 * For repeated geometry, {@link #compute()} stores particle data and coordinates;
 * recipient-dependent theme colors are still resolved during encoding.
 */
public abstract class ParticleSource {

    @Contract(pure = true)
    public PrecomputedParticleSource compute(){
        return new PrecomputedParticleSource(this, null);
    }

    @Contract(pure = true)
    public ParticleSource rotateX(double angle, double cx, double cy, double cz) {
        ParticleSource sub = this;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        return new ParticleSource() {
            @Override
            public void doWrite(PacketBuilder writer, double baseX, double baseY, double baseZ) {
                sub.doWrite((particle, x, y, z, xDist, yDist, zDist) -> {
                    double newY = cy + (y - cy) * cos - (z - cz) * sin;
                    double newZ = cz + (y - cy) * sin + (z - cz) * cos;
                    writer.append(particle, x, newY, newZ, xDist, yDist, zDist);
                }, baseX, baseY, baseZ);
            }
        };
    }

    @Contract(pure = true)
    public ParticleSource rotateY(double angle, double cx, double cy, double cz) {
        ParticleSource sub = this;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        return new ParticleSource() {
            @Override
            public void doWrite(PacketBuilder writer, double baseX, double baseY, double baseZ) {
                sub.doWrite((particle, x, y, z, xDist, yDist, zDist) -> {
                    double newX = cx + (x - cx) * cos + (z - cz) * sin;
                    double newZ = cz + -(x - cx) * sin + (z - cz) * cos;
                    writer.append(particle, newX, y, newZ, xDist, yDist, zDist);
                }, baseX, baseY, baseZ);
            }
        };
    }

    @Contract(pure = true)
    public ParticleSource rotateZ(double angle, double cx, double cy, double cz) {
        ParticleSource sub = this;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        return new ParticleSource() {
            @Override
            public void doWrite(PacketBuilder writer, double baseX, double baseY, double baseZ) {
                sub.doWrite((particle, x, y, z, xDist, yDist, zDist) -> {
                    double newX = cx + (x - cx) * cos - (y - cy) * sin;
                    double newY = cy + (x - cx) * sin + (y - cy) * cos;
                    writer.append(particle, newX, newY, z, xDist, yDist, zDist);
                }, baseX, baseY, baseZ);
            }
        };
    }

    /**
     * Defines the particle generation pattern for this source.
     * <p>
     * Implementations should use {@code writer.append(...)} to spawn individual particles
     * at positions relative to the base coordinates. All particles written to the same
     * builder are encoded into one buffer containing their individual packets.
     *
     * @param writer the packet builder to write particles to
     * @param baseX the base X coordinate
     * @param baseY the base Y coordinate
     * @param baseZ the base Z coordinate
     */
    public abstract void doWrite(PacketBuilder writer, double baseX, double baseY, double baseZ);

    /**
     * Creates a new ParticleSource with positional offset applied.
     * <p>
     * The returned source will generate the same particle pattern, but shifted by
     * the specified offset. This is useful for creating multiple instances of the
     * same pattern at different locations.
     * <p>
     * Example:
     * <pre>{@code
     * ParticleSource sphere = ...;
     * ParticleSource shiftedSphere = sphere.shift(0, 10, 0); // 10 blocks higher
     * }</pre>
     *
     * @param x the X offset
     * @param y the Y offset
     * @param z the Z offset
     * @return a new ParticleSource with the offset applied
     */
    @Contract(pure = true, value = "_, _, _ -> new")
    public ParticleSource shift(double x, double y, double z) {
        ParticleSource sub = this;
        return new ParticleSource() {
            @Override
            public void doWrite(PacketBuilder writer, double baseX, double baseY, double baseZ) {
                sub.doWrite(writer, x + baseX, y + baseY, z + baseZ);
            }
        };
    }

    /**
     * Combines this source with other sources into a composite pattern.
     * <p>
     * The returned source will execute all patterns sequentially at the same base
     * coordinates. All particle packets from the sources are encoded into one
     * buffer per recipient.
     * <p>
     * Example:
     * <pre>{@code
     * ParticleSource sphere = ...;
     * ParticleSource circle = ...;
     * ParticleSource combined = sphere.and(circle); // Both patterns at same location
     * ParticleSource twoSpheres = sphere.and(sphere.shift(0, 10, 0)); // Two spheres
     * }</pre>
     *
     * @param other additional sources to combine with this one
     * @return a new composite ParticleSource
     */
    @Contract(pure = true, value = "_ -> new")
    public ParticleSource and(ParticleSource... other) {
        if (other.length == 0) return this;
        if (other.length == 1) return of(this, other[0]);

        ParticleSource[] arr = new ParticleSource[other.length + 1];
        arr[0] = this;
        System.arraycopy(other, 0, arr, 1, other.length);
        return of(arr);
    }

    /**
     * Creates a composite ParticleSource from multiple sources.
     * <p>
     * The returned source will execute all provided sources sequentially at the
     * same base coordinates. This is equivalent to chaining multiple {@link #and(ParticleSource...)}
     * calls but more efficient for large numbers of sources.
     * <p>
     * Example:
     * <pre>{@code
     * ParticleSource multi = ParticleSource.of(source1, source2, source3);
     * }</pre>
     *
     * @param particleSources the sources to combine
     * @return a new composite ParticleSource
     */
    @Contract(pure = true)
    public static ParticleSource of(ParticleSource... particleSources) {
        return new ParticleSource() {
            @Override
            public void doWrite(PacketBuilder writer, double baseX, double baseY, double baseZ) {
                for (ParticleSource particleSource : particleSources) {
                    particleSource.doWrite(writer, baseX, baseY, baseZ);
                }
            }
        };
    }
}
