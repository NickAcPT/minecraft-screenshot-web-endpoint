package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

    @Inject(method = "renderDebugCrosshair", at = @At("HEAD"), cancellable = true)
    private void mse$hideDebugCrosshair(CallbackInfo ci) {
        if (MinecraftClient.getInstance().gameRenderer.isRenderingPanorama()) {
            ci.cancel();
        }
    }

}

