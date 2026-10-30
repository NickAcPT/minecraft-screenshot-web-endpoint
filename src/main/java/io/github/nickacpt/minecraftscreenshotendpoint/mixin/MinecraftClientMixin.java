package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import io.github.nickacpt.minecraftscreenshotendpoint.MinecraftScreenshotEndpointMod;
import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueue;
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueueEntry;
import io.github.nickacpt.minecraftscreenshotendpoint.render.ScreenshotBlockOverrides;
import io.github.nickacpt.minecraftscreenshotendpoint.render.ScreenshotStencil;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin implements ScreenshotTaskHolder {

    @Shadow @Final public GameRenderer gameRenderer;

    @Shadow @Final public LevelRenderer levelRenderer;

    @Shadow public @Nullable LocalPlayer player;

    @Shadow public @Nullable ClientLevel level;

    @Unique
    private ScreenshotQueueEntry mse$currentScreenshotEntryTask;

    @Inject(method = "close", at = @At("HEAD"))
    private void mse$stopServer(CallbackInfo ci) {
        MinecraftScreenshotEndpointMod.stopServer();
    }

    @Override
    public @Nullable ScreenshotQueueEntry mse$getCurrentScreenshotEntryTask() {
        return mse$currentScreenshotEntryTask;
    }

    @Override
    public void mse$setCurrentScreenshotEntryTask(ScreenshotQueueEntry task) {
        if (mse$currentScreenshotEntryTask == task) return;
        mse$currentScreenshotEntryTask = task;
    }

    /**
     * Renders the next queued screenshot before the regular frame, mirroring vanilla's panoramic screenshot:
     * the main render target is temporarily resized, the level is rendered from the screenshot camera,
     * and the result is copied into the entry's own render target. The regular frame then redraws the main target.
     */
    @Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;update(Lnet/minecraft/client/DeltaTracker;)V"))
    private void mse$renderScreenshot(boolean advanceGameTime, CallbackInfo ci) {
        if (this.player == null || this.level == null) return;

        var task = ScreenshotQueue.INSTANCE.getNextEntry();
        if (task == null) return;

        Camera camera = this.gameRenderer.mainCamera();
        Entity previousCameraEntity = camera.entity();
        RenderTarget mainTarget = this.gameRenderer.mainRenderTarget();
        int previousWidth = mainTarget.width;
        int previousHeight = mainTarget.height;

        mse$setCurrentScreenshotEntryTask(task);
        try {
            camera.setEntity(task.getCameraEntity());
            mainTarget.resize(task.getSettings().getWidth(), task.getSettings().getHeight());

            this.gameRenderer.update(DeltaTracker.ONE);
            // Enabled after the camera update so panoramic state captures the screenshot camera's direction
            camera.enablePanoramicMode();
            // Overrides cover this screenshot's extraction and draw and are released before the regular frame runs.
            // They must be active before extract: Sodium compiles and selects terrain there.
            AutoCloseable overrides = null;
            try {
                overrides = ScreenshotBlockOverrides.begin(camera.position(), task.getSettings().getBlockOverrides());
                this.gameRenderer.extract(DeltaTracker.ONE, true);
                mse$updateGlobalSettingsUniform();
                ScreenshotStencil.begin(mainTarget, task.getSettings().getStencils(), mainTarget.height);
                this.gameRenderer.renderLevel();
            } finally {
                ScreenshotStencil.end();
                ScreenshotBlockOverrides.end(overrides);
            }
            mse$rotatePerFrameBuffers();

            task.getFramebuffer().copyColorFrom(mainTarget);
        } finally {
            camera.disablePanoramicMode();
            camera.setEntity(previousCameraEntity);
            mainTarget.resize(previousWidth, previousHeight);
            mse$setCurrentScreenshotEntryTask(null);
        }

        task.notifyDone();
    }

    /**
     * Vanilla only writes the Globals UBO (camera block pos/offset, screen size) in {@code GameRenderer#render},
     * which the screenshot pass skips. Without this, shaders reading it (e.g. the terrain shader used by
     * Axiom's chunk render overrider) would use the previous frame's player camera. The next regular frame
     * rewrites it, so no restore is needed.
     */
    @Unique
    private void mse$updateGlobalSettingsUniform() {
        GameRenderState state = this.gameRenderer.gameRenderState();
        OptionsRenderState options = state.optionsRenderState;
        LevelRenderState levelState = state.levelRenderState;

        ((GameRendererAccessor) this.gameRenderer).getGlobalSettingsUniform().update(
                state.windowRenderState.width,
                state.windowRenderState.height,
                options.glintStrength,
                state.shouldRenderLevel ? levelState.gameTime : 0L,
                state.shouldRenderLevel ? levelState.worldPartialTicks : 0.0F,
                options.menuBackgroundBlurriness,
                levelState.cameraRenderState.pos,
                options.textureFiltering == TextureFilteringMethod.RGSS
        );
    }

    /**
     * The cloud and fog UBOs are unsynchronized mapped ring buffers that vanilla rotates once per frame.
     * Without rotating after the screenshot pass, the regular frame overwrites the same buffer before the GPU
     * executes the screenshot's draws, so the screenshot would use the player's cloud height and fog.
     */
    @Unique
    private void mse$rotatePerFrameBuffers() {
        this.levelRenderer.cloudRenderer().endFrame();
        ((GameRendererAccessor) this.gameRenderer).getFogRenderer().endFrame();
    }
}
