package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Inject(method = "calculateFov", at = @At("HEAD"), cancellable = true)
    private void mse$overrideFov(CallbackInfoReturnable<Float> cir) {
        var holder = (ScreenshotTaskHolder) Minecraft.getInstance();
        var task = holder.mse$getCurrentScreenshotEntryTask();

        if (task != null) {
            var fov = task.getSettings().getFov();
            cir.setReturnValue((float) fov);
        }
    }
}
