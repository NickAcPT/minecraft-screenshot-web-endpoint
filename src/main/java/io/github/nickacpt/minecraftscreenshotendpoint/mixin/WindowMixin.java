package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueueEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public class WindowMixin {

    @Inject(method = "getFramebufferWidth", at = @At("HEAD"), cancellable = true)
    private void mse$overrideFramebufferWidth(CallbackInfoReturnable<Integer> info) {
        ScreenshotTaskHolder holder = (ScreenshotTaskHolder) MinecraftClient.getInstance();
        var task = holder.mse$getCurrentScreenshotEntryTask();

        if (task != null) {
            info.setReturnValue(task.getSettings().getWidth());
        }
    }

    @Inject(method = "getFramebufferHeight", at = @At("HEAD"), cancellable = true)
    private void mse$overrideFramebufferHeight(CallbackInfoReturnable<Integer> info) {
        ScreenshotTaskHolder holder = (ScreenshotTaskHolder) MinecraftClient.getInstance();
        var task = holder.mse$getCurrentScreenshotEntryTask();

        if (task != null) {
            info.setReturnValue(task.getSettings().getHeight());
        }
    }
}
