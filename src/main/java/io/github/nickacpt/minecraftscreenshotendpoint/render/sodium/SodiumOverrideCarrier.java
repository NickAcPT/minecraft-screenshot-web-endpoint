package io.github.nickacpt.minecraftscreenshotendpoint.render.sodium;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Carries the screenshot's block overrides on a Sodium {@code ChunkRenderContext}, so that the slice copied for that
 * one section build (on a builder thread) applies them without reading any shared state. Implemented on
 * {@code ChunkRenderContext} by {@code ChunkRenderContextMixin}.
 */
public interface SodiumOverrideCarrier {
    @Nullable Long2ObjectMap<BlockState> mse$getOverrides();

    void mse$setOverrides(@Nullable Long2ObjectMap<BlockState> overrides);
}
