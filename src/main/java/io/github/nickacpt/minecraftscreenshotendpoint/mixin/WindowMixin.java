package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public class WindowMixin {

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void mse$overrideFramebufferWidth(CallbackInfoReturnable<Integer> info) {
        ScreenshotTaskHolder holder = (ScreenshotTaskHolder) Minecraft.getInstance();
        var task = holder.mse$getCurrentScreenshotEntryTask();

        if (task != null) {
            info.setReturnValue(task.getSettings().getWidth());
        }
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void mse$overrideFramebufferHeight(CallbackInfoReturnable<Integer> info) {
        ScreenshotTaskHolder holder = (ScreenshotTaskHolder) Minecraft.getInstance();
        var task = holder.mse$getCurrentScreenshotEntryTask();

        if (task != null) {
            info.setReturnValue(task.getSettings().getHeight());
        }
    }
}
