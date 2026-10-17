package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void mse$overrideshouldRenderBlockOutline(CallbackInfoReturnable<Boolean> cir) {
        var holder = (ScreenshotTaskHolder) Minecraft.getInstance();
        var task = holder.mse$getCurrentScreenshotEntryTask();

        if (task != null) {
            cir.setReturnValue(false);
        }
    }
}
