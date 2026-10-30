package io.github.nickacpt.minecraftscreenshotendpoint.mixin.sodium;

import io.github.nickacpt.minecraftscreenshotendpoint.render.sodium.SodiumOverrideCarrier;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds the override carrier to Sodium's per-build context. The context is created on the render thread and copied on
 * a builder thread, so the overrides travel with the build without any shared mutable state.
 */
@Mixin(ChunkRenderContext.class)
public abstract class ChunkRenderContextMixin implements SodiumOverrideCarrier {
    @Unique
    private @Nullable Long2ObjectMap<BlockState> mse$overrides;

    @Override
    public @Nullable Long2ObjectMap<BlockState> mse$getOverrides() {
        return this.mse$overrides;
    }

    @Override
    public void mse$setOverrides(@Nullable Long2ObjectMap<BlockState> overrides) {
        this.mse$overrides = overrides;
    }
}
