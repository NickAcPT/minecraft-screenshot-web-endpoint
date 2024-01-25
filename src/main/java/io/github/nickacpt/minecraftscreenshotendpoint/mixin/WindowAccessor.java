package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Window.class)
public interface WindowAccessor {

    @Invoker
    void invokeOnFramebufferSizeChanged(long var1, int var3, int var4);
}
