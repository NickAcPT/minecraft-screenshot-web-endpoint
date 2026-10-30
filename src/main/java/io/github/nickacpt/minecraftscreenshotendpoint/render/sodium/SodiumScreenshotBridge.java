package io.github.nickacpt.minecraftscreenshotendpoint.render.sodium;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Entry point for the screenshot pass when Sodium is loaded. Only referenced when Sodium is present (see
 * {@code ScreenshotBlockOverrides#begin}), so Sodium classes are never loaded otherwise.
 *
 * <p>The overrides are published here for the render-thread hooks that create chunk builds. Builder threads never
 * read this state; they receive the overrides through their {@link SodiumOverrideCarrier}.
 */
public final class SodiumScreenshotBridge {
    /** Upper bound on the time spent waiting for the private terrain build to settle within one screenshot pass. */
    public static final long SETTLE_TIMEOUT_NANOS = 30_000_000_000L;

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile @Nullable Long2ObjectMap<BlockState> activeOverrides;

    private SodiumScreenshotBridge() {
    }

    /** The overrides of the screenshot pass being rendered, or null outside one. */
    public static @Nullable Long2ObjectMap<BlockState> activeOverrides() {
        return activeOverrides;
    }

    /**
     * Starts an override pass on Sodium's terrain renderer. The returned handle ends it and must always be closed.
     */
    public static AutoCloseable begin(Long2ObjectMap<BlockState> overrides) {
        SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();
        if (renderer == null) {
            throw new IllegalStateException("Sodium world renderer is not active");
        }
        SodiumScreenshotHost host = (SodiumScreenshotHost) (Object) renderer;
        activeOverrides = overrides;
        try {
            host.mse$beginOverridePass();
        } catch (RuntimeException | Error e) {
            activeOverrides = null;
            throw e;
        }
        return () -> {
            try {
                host.mse$endOverridePass();
            } finally {
                activeOverrides = null;
            }
        };
    }

    /** Called when the private terrain build did not settle in time; the pass draws whatever is ready. */
    public static void onSettleTimeout() {
        LOGGER.warn("Screenshot block overrides: terrain build did not settle within {} s", SETTLE_TIMEOUT_NANOS / 1_000_000_000L);
    }
}
