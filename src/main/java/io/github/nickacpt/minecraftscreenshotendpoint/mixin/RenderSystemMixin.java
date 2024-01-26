package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueue;
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueueEntry;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Debug(export = true)
@Mixin(RenderSystem.class)
public class RenderSystemMixin {
    @Unique
    private static boolean skipNextFlipFrame = false;

    @Redirect(method = "flipFrame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V"))
    private static void flipFrame(long window) {
        var currentTask = ((ScreenshotTaskHolder) MinecraftClient.getInstance()).mse$getCurrentScreenshotEntryTask();
        if (currentTask != null) {
            System.out.println("Skipping current and next frame");
            skipNextFlipFrame = true;
            return;
        }
        
        if (skipNextFlipFrame) {
            System.out.println("skipNextFlipFrame = " + skipNextFlipFrame);
            skipNextFlipFrame = false;
            return;
        }
        GLFW.glfwSwapBuffers(window);
    }
}
