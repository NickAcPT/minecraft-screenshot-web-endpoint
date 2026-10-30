package io.github.nickacpt.minecraftscreenshotendpoint.mixin.sodium;

import io.github.nickacpt.minecraftscreenshotendpoint.render.sodium.SodiumOverrideCarrier;
import io.github.nickacpt.minecraftscreenshotendpoint.render.sodium.SodiumScreenshotBridge;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hooks Sodium's section manager so that, during a screenshot override pass, its chunk builds receive the overrides
 * and empty sections are still built.
 *
 * <p>{@code createRebuildTask} attaches the overrides to the context of each build, which is the only data the builder
 * thread reads. {@code onSectionAdded} normally files an air-only section as empty without building it. That would
 * drop an override placed into such a section, so during the pass those sections are treated as non-empty.
 */
@Mixin(RenderSectionManager.class)
public abstract class RenderSectionManagerMixin {
    @Redirect(
        method = "createRebuildTask",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/world/LevelSlice;prepare(" +
                "Lnet/minecraft/world/level/Level;" +
                "Lnet/minecraft/core/SectionPos;" +
                "Lnet/caffeinemc/mods/sodium/client/world/cloned/ClonedChunkSectionCache;)" +
                "Lnet/caffeinemc/mods/sodium/client/world/cloned/ChunkRenderContext;"
        )
    )
    private static ChunkRenderContext mse$prepareWithOverrides(Level level, SectionPos pos, ClonedChunkSectionCache cache) {
        ChunkRenderContext context = LevelSlice.prepare(level, pos, cache);
        Long2ObjectMap<BlockState> overrides = SodiumScreenshotBridge.activeOverrides();
        if (context != null && overrides != null) {
            ((SodiumOverrideCarrier) (Object) context).mse$setOverrides(overrides);
        }
        return context;
    }

    @Redirect(
        method = "onSectionAdded",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;hasOnlyAir()Z")
    )
    private static boolean mse$keepSectionsDuringPass(LevelChunkSection section) {
        return SodiumScreenshotBridge.activeOverrides() == null && section.hasOnlyAir();
    }
}
