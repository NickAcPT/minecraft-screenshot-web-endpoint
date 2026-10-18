package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import io.github.nickacpt.minecraftscreenshotendpoint.MinecraftScreenshotEndpointMod;
import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueue;
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueueEntry;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
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
            this.gameRenderer.extract(DeltaTracker.ONE, true);
            this.gameRenderer.renderLevel();

            task.getFramebuffer().copyColorFrom(mainTarget);
        } finally {
            camera.disablePanoramicMode();
            camera.setEntity(previousCameraEntity);
            mainTarget.resize(previousWidth, previousHeight);
            mse$setCurrentScreenshotEntryTask(null);
        }

        task.notifyDone();
    }
}
