package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Window.class)
public interface WindowAccessor {

    @Invoker
    void invokeOnFramebufferResize(int width, int height);
}
