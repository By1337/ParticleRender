package com.by1337.themes;

import org.bukkit.entity.Player;

/** Test fixture only: the renderer has no dependency on the real BThemes artifact. */
public final class BThemes {
    public static int rgb = 0xFFFFFF;
    public static Player lastPlayer;

    public static int getRgb(Player player) {
        lastPlayer = player;
        return rgb;
    }
}
