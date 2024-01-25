package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.FramebufferOverwritable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin implements FramebufferOverwritable {

    @Unique
    private Framebuffer overriden$framebuffer;

    @Override
    public void overriden$setFramebuffer(Framebuffer framebuffer) {
        overriden$framebuffer = framebuffer;
    }

    @Inject(at = @At("HEAD"), method = "getFramebuffer", cancellable = true)
    private void getFramebufferHook(CallbackInfoReturnable<Framebuffer> info) {
        if (overriden$framebuffer != null) {
            info.setReturnValue(overriden$framebuffer);
        }
    }
}
