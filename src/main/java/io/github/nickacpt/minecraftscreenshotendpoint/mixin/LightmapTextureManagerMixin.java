package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {

    @Final
    @Shadow
    private MinecraftClient client;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void mse$preventCrashPlayerIsNull(CallbackInfo ci) {
        if (this.client.player == null && client.gameRenderer != null && client.gameRenderer.isRenderingPanorama()) {
            //noinspection CallToPrintStackTrace
            new RuntimeException("Prevented lightmap update crash during screenshot capture.").printStackTrace();
            ci.cancel();
        }
    }

}
