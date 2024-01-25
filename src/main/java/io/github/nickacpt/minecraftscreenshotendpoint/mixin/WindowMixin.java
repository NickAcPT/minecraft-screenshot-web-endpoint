package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.FramebufferSizeOverwritable;
import io.github.nickacpt.minecraftscreenshotendpoint.OriginalWindowFramebufferSize;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public class WindowMixin implements OriginalWindowFramebufferSize, FramebufferSizeOverwritable {

    @Shadow
    private int framebufferWidth;
    @Shadow
    private int framebufferHeight;

    @Unique
    private Integer mse$framebufferWidth;
    @Unique
    private Integer mse$framebufferHeight;

    @Override
    public int mse$originalFramebufferWidth() {
        return framebufferWidth;
    }

    @Override
    public int mse$originalFramebufferHeight() {
        return framebufferHeight;
    }

    @Override
    public void mse$setFramebufferWidth(Integer value) {
        this.mse$framebufferWidth = value;
    }

    @Override
    public void mse$setFramebufferHeight(Integer value) {
        this.mse$framebufferHeight = value;
    }

    @Inject(method = "getFramebufferWidth", at = @At("HEAD"), cancellable = true)
    private void getFramebufferWidthHook(CallbackInfoReturnable<Integer> info) {
        if (mse$framebufferWidth != null) {
            info.setReturnValue(mse$framebufferWidth);
        }
    }

    @Inject(method = "getFramebufferHeight", at = @At("HEAD"), cancellable = true)
    private void getFramebufferHeightHook(CallbackInfoReturnable<Integer> info) {
        if (mse$framebufferHeight != null) {
            info.setReturnValue(mse$framebufferHeight);
        }
    }

}
