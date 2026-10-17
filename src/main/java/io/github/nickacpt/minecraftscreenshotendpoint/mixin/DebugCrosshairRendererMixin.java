package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DebugCrosshairRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugCrosshairRenderer.class)
public abstract class DebugCrosshairRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mse$hideDebugCrosshair(CallbackInfo ci) {
        if (((ScreenshotTaskHolder) Minecraft.getInstance()).mse$getCurrentScreenshotEntryTask() != null) {
            ci.cancel();
        }
    }

}
