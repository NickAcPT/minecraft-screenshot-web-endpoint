package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueue;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Debug(export = true)
@Mixin(RenderSystem.class)
public class RenderSystemMixin {

    @Redirect(method = "flipFrame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V"))
    private static void flipFrame(long window) {
        if (ScreenshotQueue.INSTANCE.getSkipNextFrameFlip()) {
            System.out.println("Skipping current and next frame");
            return;
        }

        GLFW.glfwSwapBuffers(window);
    }
}
