package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

/*
    @Inject(method = {
            "getEntityOutlinesFramebuffer",
            "getTranslucentFramebuffer",
            "getEntityFramebuffer",
            "getParticlesFramebuffer",
            "getWeatherFramebuffer",
            "getCloudsFramebuffer",
    }, at = @At("HEAD"), cancellable = true)
    private void mse$overrideFramebuffers(CallbackInfoReturnable<Framebuffer> cir) {
        var holder = (ScreenshotTaskHolder) MinecraftClient.getInstance();
        var task = holder.mse$getCurrentScreenshotEntryTask();

        if (task != null) {
            var fb = task.getFramebuffer();
            cir.setReturnValue(fb);
        }
    }
*/
}
