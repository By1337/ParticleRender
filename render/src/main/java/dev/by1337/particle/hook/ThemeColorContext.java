package dev.by1337.particle.hook;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToIntFunction;

/** Optional theme provider and the color snapshot for the current encoding operation. */
public final class ThemeColorContext {
    public static final int WHITE = 0xFFFFFF;
    private static final AtomicReference<ToIntFunction<UUID>> PROVIDER = new AtomicReference<>();
    private static final ThreadLocal<Integer> COLOR = new ThreadLocal<>();

    private ThemeColorContext() {}

    public static void setProvider(@Nullable ToIntFunction<UUID> provider) {
        PROVIDER.set(provider);
    }

    public static void clearProvider(ToIntFunction<UUID> provider) {
        PROVIDER.compareAndSet(provider, null);
    }

    public static int resolve(@Nullable UUID viewer) {
        var provider = PROVIDER.get();
        if (viewer == null || provider == null) return WHITE;
        try {
            return valid(provider.applyAsInt(viewer));
        } catch (RuntimeException | LinkageError ignored) {
            return WHITE;
        }
    }

    public static int current() {
        Integer color = COLOR.get();
        return color == null ? WHITE : color;
    }

    public static Scope push(int rgb) {
        Integer previous = COLOR.get();
        COLOR.set(valid(rgb));
        return () -> {
            if (previous == null) COLOR.remove();
            else COLOR.set(previous);
        };
    }

    private static int valid(int rgb) {
        return (rgb & ~0xFFFFFF) == 0 ? rgb : WHITE;
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override void close();
    }
}
