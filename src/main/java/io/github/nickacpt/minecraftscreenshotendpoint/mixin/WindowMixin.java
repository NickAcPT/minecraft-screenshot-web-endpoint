package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.OriginalWindowFramebufferSize;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Window.class)
public class WindowMixin implements OriginalWindowFramebufferSize {

    @Shadow
    private int framebufferWidth;
    @Shadow
    private int framebufferHeight;

    @Override
    public int originalFramebufferWidth() {
        return framebufferWidth;
    }

    @Override
    public int originalFramebufferHeight() {
        return framebufferHeight;
    }
}
