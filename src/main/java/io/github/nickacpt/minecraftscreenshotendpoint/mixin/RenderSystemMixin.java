package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueue;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Debug(export = true)
@Mixin(RenderSystem.class)
public class RenderSystemMixin {

    @Inject(method = "flipFrame", at = @At(value = "HEAD"), remap = false, cancellable = true)
    private static void flipFrame(CallbackInfo ci) {
        if (ScreenshotQueue.INSTANCE.getSkipNextFrameFlip()) {
            ci.cancel();
        }
    }
}
