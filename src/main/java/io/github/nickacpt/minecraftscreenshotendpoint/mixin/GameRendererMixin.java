package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    public abstract boolean isRenderingPanorama();

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void mse$overrideFov(CallbackInfoReturnable<Double> cir) {
        var holder = (ScreenshotTaskHolder) MinecraftClient.getInstance();
        var task = holder.mse$getCurrentScreenshotEntryTask();

        if (task != null) {
            var fov = task.getSettings().getFov();
            cir.setReturnValue(fov);
        }
    }

    @Inject(method = "isRenderingPanorama", at = @At("HEAD"), cancellable = true)
    private void mse$overrideRenderingPanorama(CallbackInfoReturnable<Boolean> cir) {
        var holder = (ScreenshotTaskHolder) MinecraftClient.getInstance();
        var task = holder.mse$getCurrentScreenshotEntryTask();

        if (task != null) {
            cir.setReturnValue(true);
        }
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V"), index = 0)
    private float mse$overrideRenderTickDelta(float tickDelta) {
        var holder = (ScreenshotTaskHolder) MinecraftClient.getInstance();
        var task = holder.mse$getCurrentScreenshotEntryTask();

        return task != null ? 1.0f : tickDelta;
    }

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void mse$renderWorldStart(CallbackInfo ci) {
        var holder = (ScreenshotTaskHolder) MinecraftClient.getInstance();
        var task = holder.mse$getCurrentScreenshotEntryTask();

        if (task == null) {
            var nextTask = ScreenshotQueue.INSTANCE.getNextEntry();
            holder.mse$setCurrentScreenshotEntryTask(nextTask);
        }
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void mse$renderWorldEnd(CallbackInfo ci) {
        var holder = (ScreenshotTaskHolder) MinecraftClient.getInstance();

        var task = holder.mse$getCurrentScreenshotEntryTask();

        if (task != null) {
            task.notifyDone();
            holder.mse$setCurrentScreenshotEntryTask(null);
        }
    }


    @Inject(at = @At("HEAD"), method = {
            "renderFloatingItem",
            "renderNausea",
            "renderHand",
    }, cancellable = true)
    public void mse$cancelRenderingSomeThings(CallbackInfo ci) {
        if (isRenderingPanorama()) {
            ci.cancel();
        }
    }
}
