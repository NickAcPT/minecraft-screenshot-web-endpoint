package io.github.nickacpt.minecraftscreenshotendpoint.mixin.sodium;

import io.github.nickacpt.minecraftscreenshotendpoint.render.sodium.SodiumOverrideCarrier;
import io.github.nickacpt.minecraftscreenshotendpoint.render.sodium.SodiumScreenshotBridge;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Writes the screenshot overrides into the block data a chunk build reads, after the real data has been copied.
 *
 * <p>{@code LevelSlice} holds a private copy of the 3x3x3 section neighbourhood for one build. Changing only that copy
 * lets the mesher see the override while the {@code ClientLevel}, the server and the live meshes keep the real block.
 * Block-entity maps are shared with the cloned section cache, so an overridden position's entry is removed from a
 * copy of the map, never from the shared map.
 *
 * <p>{@code prepare} returns null for air-only sections. The redirect keeps those as non-null during the pass, so an
 * override placed into an otherwise empty section is still meshed.
 */
@Mixin(LevelSlice.class)
public abstract class LevelSliceMixin {
    @Shadow
    private int originBlockX;
    @Shadow
    private int originBlockY;
    @Shadow
    private int originBlockZ;
    @Shadow
    private BoundingBox volume;
    @Shadow
    private BlockState[][] blockArrays;
    @Shadow
    private Int2ReferenceMap<BlockEntity>[] blockEntityArrays;
    @Shadow
    private Int2ReferenceMap<Object>[] blockEntityRenderDataArrays;

    @Redirect(
        method = "prepare",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;hasOnlyAir()Z")
    )
    private static boolean mse$keepSectionsDuringPass(LevelChunkSection section) {
        return SodiumScreenshotBridge.activeOverrides() == null && section.hasOnlyAir();
    }

    @Inject(method = "copyData", at = @At("RETURN"))
    private void mse$applyOverrides(ChunkRenderContext context, CallbackInfo ci) {
        Long2ObjectMap<BlockState> overrides = ((SodiumOverrideCarrier) (Object) context).mse$getOverrides();
        if (overrides == null || overrides.isEmpty()) {
            return;
        }

        for (Long2ObjectMap.Entry<BlockState> entry : overrides.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            int x = BlockPos.getX(key);
            int y = BlockPos.getY(key);
            int z = BlockPos.getZ(key);
            if (!this.volume.isInside(x, y, z)) {
                continue;
            }

            int relX = x - this.originBlockX;
            int relY = y - this.originBlockY;
            int relZ = z - this.originBlockZ;
            int sectionIndex = LevelSlice.getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4);
            int blockIndex = LevelSlice.getLocalBlockIndex(relX & 15, relY & 15, relZ & 15);

            this.blockArrays[sectionIndex][blockIndex] = entry.getValue();
            this.mse$removeBlockEntity(sectionIndex, blockIndex);
        }
    }

    @Unique
    private void mse$removeBlockEntity(int sectionIndex, int blockIndex) {
        Int2ReferenceMap<BlockEntity> blockEntities = this.blockEntityArrays[sectionIndex];
        if (blockEntities != null && blockEntities.containsKey(blockIndex)) {
            Int2ReferenceMap<BlockEntity> copy = new Int2ReferenceOpenHashMap<>(blockEntities);
            copy.remove(blockIndex);
            this.blockEntityArrays[sectionIndex] = copy;
        }

        Int2ReferenceMap<Object> renderData = this.blockEntityRenderDataArrays[sectionIndex];
        if (renderData != null && renderData.containsKey(blockIndex)) {
            Int2ReferenceMap<Object> copy = new Int2ReferenceOpenHashMap<>(renderData);
            copy.remove(blockIndex);
            this.blockEntityRenderDataArrays[sectionIndex] = copy;
        }
    }
}
