package dev.by1337.particle.hook;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Reflection-only bridge; BThemes and BLib are not compile-time dependencies. */
public final class BThemesColorHook {
    private BThemesColorHook() {}

    public static @Nullable ToIntFunction<UUID> create(ClassLoader loader, Function<UUID, Player> players) {
        final MethodHandle rgb;
        try {
            Class<?> themes = Class.forName("com.by1337.themes.BThemes", false, loader);
            rgb = MethodHandles.publicLookup().findStatic(themes, "getRgb", MethodType.methodType(int.class, Player.class));
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return null;
        }
        return viewer -> {
            try {
                Player player = players.apply(viewer);
                return player == null ? ThemeColorContext.WHITE : (int) rgb.invokeExact(player);
            } catch (Throwable ignored) {
                return ThemeColorContext.WHITE;
            }
        };
    }
}
