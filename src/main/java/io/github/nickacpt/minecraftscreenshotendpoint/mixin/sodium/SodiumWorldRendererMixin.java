package io.github.nickacpt.minecraftscreenshotendpoint.mixin.sodium;

import io.github.nickacpt.minecraftscreenshotendpoint.render.sodium.SodiumScreenshotBridge;
import io.github.nickacpt.minecraftscreenshotendpoint.render.sodium.SodiumScreenshotHost;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.locks.LockSupport;

/**
 * Renders a screenshot pass with its own terrain managers, so block overrides never touch the live terrain.
 *
 * <p>Sodium keeps all terrain state (section graph, meshes, GPU buffers, uniform buffers) in the
 * {@code RenderSectionManager} and {@code UniformBufferManager} fields. For the pass those fields are swapped for
 * private instances that build every section in view with the overrides applied. The live instances are restored
 * afterwards, so the regular frames keep their meshes.
 *
 * <p>{@code processChunkEvents} is skipped during the pass. It drains the shared {@code ChunkTracker}, which the live
 * manager needs to see the same events. The events stay queued and are processed by the next regular frame.
 *
 * <p>Chunk builds are asynchronous, so {@code setupTerrain} is repeated after it returns until the private manager is
 * idle. The next frame would otherwise draw only what was already uploaded.
 */
@Mixin(SodiumWorldRenderer.class)
public abstract class SodiumWorldRendererMixin implements SodiumScreenshotHost {
    @Shadow
    private ClientLevel level;
    @Shadow
    private int renderDistance;
    @Shadow
    private boolean useTranslucencySorting;
    @Shadow
    private RenderSectionManager renderSectionManager;
    @Shadow
    private UniformBufferManager uniformBufferManager;

    @Unique
    private RenderSectionManager mse$liveRenderSectionManager;
    @Unique
    private UniformBufferManager mse$liveUniformBufferManager;
    @Unique
    private boolean mse$passActive;
    @Unique
    private boolean mse$settling;

    @Override
    public void mse$beginOverridePass() {
        if (this.mse$passActive) {
            throw new IllegalStateException("Screenshot override pass is already active");
        }

        SortBehavior sortBehavior = this.useTranslucencySorting ? SortBehavior.DYNAMIC_DEFER_NEARBY_ZERO_FRAMES : SortBehavior.OFF;
        RenderSectionManager sections = new RenderSectionManager(this.level, this.renderDistance, sortBehavior);
        UniformBufferManager uniforms;
        try {
            uniforms = new UniformBufferManager(this.level, this.renderDistance);
        } catch (RuntimeException | Error e) {
            sections.destroy();
            throw e;
        }

        this.mse$liveRenderSectionManager = this.renderSectionManager;
        this.mse$liveUniformBufferManager = this.uniformBufferManager;
        this.renderSectionManager = sections;
        this.uniformBufferManager = uniforms;
        this.mse$passActive = true;

        // The ready set can still list chunks whose unload event has not been processed yet; skip those.
        ClientLevel level = this.level;
        ChunkTracker.forEachChunk(
            ChunkTrackerHolder.get(level).getReadyChunks(),
            (x, z) -> {
                if (level.hasChunk(x, z)) {
                    sections.onChunkAdded(x, z);
                }
            }
        );
    }

    @Override
    public void mse$endOverridePass() {
        if (!this.mse$passActive) {
            return;
        }

        RenderSectionManager sections = this.renderSectionManager;
        UniformBufferManager uniforms = this.uniformBufferManager;

        this.renderSectionManager = this.mse$liveRenderSectionManager;
        this.uniformBufferManager = this.mse$liveUniformBufferManager;
        this.mse$liveRenderSectionManager = null;
        this.mse$liveUniformBufferManager = null;
        this.mse$passActive = false;

        if (sections != null) {
            sections.destroy();
        }
        if (uniforms != null) {
            uniforms.delete();
        }
    }

    @Inject(method = "processChunkEvents", at = @At("HEAD"), cancellable = true)
    private void mse$skipChunkEventsDuringPass(CallbackInfo ci) {
        if (this.mse$passActive) {
            ci.cancel();
        }
    }

    @Inject(method = "setupTerrain", at = @At("RETURN"))
    private void mse$settleOverridePass(
        Camera camera,
        Viewport viewport,
        FogParameters fogParameters,
        boolean useOcclusionCulling,
        boolean updateChunksImmediately,
        Matrix4f cullMatrix,
        CallbackInfo ci
    ) {
        if (!this.mse$passActive || this.mse$settling) {
            return;
        }

        this.mse$settling = true;
        try {
            long deadline = System.nanoTime() + SodiumScreenshotBridge.SETTLE_TIMEOUT_NANOS;
            int quietFrames = 0;
            while (quietFrames < 2) {
                if (System.nanoTime() > deadline) {
                    SodiumScreenshotBridge.onSettleTimeout();
                    return;
                }

                ((SodiumWorldRenderer) (Object) this).setupTerrain(
                    camera, viewport, fogParameters, useOcclusionCulling, true, cullMatrix
                );

                if (this.mse$isPassIdle()) {
                    quietFrames++;
                } else {
                    quietFrames = 0;
                    LockSupport.parkNanos(1_000_000L);
                }
            }
        } finally {
            this.mse$settling = false;
        }
    }

    @Unique
    private boolean mse$isPassIdle() {
        RenderSectionManager sections = this.renderSectionManager;
        return !sections.needsUpdate()
            && sections.getBuilder().isBuildQueueEmpty()
            && sections.getBuilder().getBusyThreadCount() == 0;
    }
}
