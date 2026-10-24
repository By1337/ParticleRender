package dev.by1337.particle.hook;

import com.by1337.themes.BThemes;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.Assert.*;

public class BThemesColorHookTest {
    @Test
    public void optionalHookKeepsLiveRgbAndUsesTheRequestedPlayer() {
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class}, (p, method, args) -> null);
        UUID viewer = UUID.randomUUID();
        var hook = BThemesColorHook.create(getClass().getClassLoader(), id -> {
            assertEquals(viewer, id);
            return player;
        });
        assertNotNull(hook);
        BThemes.rgb = 0x123456;
        assertEquals(0x123456, hook.applyAsInt(viewer));
        assertSame(player, BThemes.lastPlayer);
        BThemes.rgb = 0xAABBCC;
        assertEquals(0xAABBCC, hook.applyAsInt(viewer));
    }

    @Test
    public void missingBThemesDoesNotRequireAnyLibraryApi() {
        ClassLoader withoutThemes = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("com.by1337.themes.BThemes")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        assertNull(BThemesColorHook.create(withoutThemes, id -> null));
    }

    @Test
    public void unavailablePlayerOrFailedProviderUsesWhite() {
        UUID viewer = UUID.randomUUID();
        var missingPlayer = BThemesColorHook.create(getClass().getClassLoader(), id -> null);
        assertNotNull(missingPlayer);
        assertEquals(ThemeColorContext.WHITE, missingPlayer.applyAsInt(viewer));
        var failedLookup = BThemesColorHook.create(getClass().getClassLoader(), id -> { throw new IllegalStateException("offline"); });
        assertNotNull(failedLookup);
        assertEquals(ThemeColorContext.WHITE, failedLookup.applyAsInt(viewer));
    }
}
