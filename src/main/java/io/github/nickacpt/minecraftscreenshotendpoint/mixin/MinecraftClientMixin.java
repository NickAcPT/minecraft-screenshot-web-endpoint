package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueue;
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueueEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin implements ScreenshotTaskHolder {

    @Shadow public abstract Framebuffer getFramebuffer();

    @Unique
    private ScreenshotQueueEntry mse$currentScreenshotEntryTask;

    @Override
    public @Nullable ScreenshotQueueEntry mse$getCurrentScreenshotEntryTask() {
        return mse$currentScreenshotEntryTask;
    }

    @Inject(at = @At("HEAD"), method = "getFramebuffer", cancellable = true)
    private void getFramebufferHook(CallbackInfoReturnable<Framebuffer> info) {
        if (mse$currentScreenshotEntryTask != null) {
            info.setReturnValue(mse$currentScreenshotEntryTask.getFramebuffer());
        }
    }

    @Override
    public void mse$setCurrentScreenshotEntryTask(ScreenshotQueueEntry task) {
        if (mse$currentScreenshotEntryTask == task) return;

        if (task == null) {
            System.out.println("Ending screenshot task");
        } else {
            System.out.println("Starting screenshot task " + task);
        }
        System.out.println("[STATS] Screenshot queue:" + ScreenshotQueue.INSTANCE.getSize());

        mse$currentScreenshotEntryTask = task;

        // Update the framebuffer to the new one
        var newFramebuffer = this.getFramebuffer();
        newFramebuffer.beginWrite(false);

        // Notify the game about the new framebuffer size
        var window = MinecraftClient.getInstance().getWindow();

        System.out.println("newFramebuffer = " + newFramebuffer.textureWidth + "x" + newFramebuffer.textureHeight);
        ((WindowAccessor) (Object) window).invokeOnFramebufferSizeChanged(window.getHandle(), newFramebuffer.textureWidth, newFramebuffer.textureHeight);

    }

    @Inject(at = @At("HEAD"), method = "getCameraEntity", cancellable = true)
    private void mse$overrideCameraEntity(CallbackInfoReturnable<Entity> cir) {
        var holder = (ScreenshotTaskHolder) MinecraftClient.getInstance();
        var task = holder.mse$getCurrentScreenshotEntryTask();

        if (task != null) {
            cir.setReturnValue(task.getCameraEntity());
        }
    }
}
