package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private Entity entity;

    @Shadow private float eyeHeight;

    @Shadow private float eyeHeightOld;

    @Unique
    private boolean mse$eyeHeightOverridden;

    @Unique
    private float mse$savedEyeHeight;

    @Unique
    private float mse$savedEyeHeightOld;

    @Inject(method = "calculateFov", at = @At("HEAD"), cancellable = true)
    private void mse$overrideFov(CallbackInfoReturnable<Float> cir) {
        var holder = (ScreenshotTaskHolder) Minecraft.getInstance();
        var task = holder.mse$getCurrentScreenshotEntryTask();

        if (task != null) {
            var fov = task.getSettings().getFov();
            cir.setReturnValue((float) fov);
        }
    }

    /**
     * The camera's eye height is smoothed towards the player's eye height in {@code Camera#tick}, so while the
     * player is sneaking it would lower the screenshot camera too. Use the screenshot entity's eye height instead
     * and restore the player's smoothing state afterwards.
     */
    @Inject(method = "alignWithEntity", at = @At("HEAD"))
    private void mse$overrideEyeHeight(float partialTicks, CallbackInfo ci) {
        var holder = (ScreenshotTaskHolder) Minecraft.getInstance();
        if (holder.mse$getCurrentScreenshotEntryTask() == null) return;

        mse$eyeHeightOverridden = true;
        mse$savedEyeHeight = eyeHeight;
        mse$savedEyeHeightOld = eyeHeightOld;
        eyeHeight = eyeHeightOld = entity.getEyeHeight();
    }

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void mse$restoreEyeHeight(float partialTicks, CallbackInfo ci) {
        if (!mse$eyeHeightOverridden) return;

        mse$eyeHeightOverridden = false;
        eyeHeight = mse$savedEyeHeight;
        eyeHeightOld = mse$savedEyeHeightOld;
    }
}
