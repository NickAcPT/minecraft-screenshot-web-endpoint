package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class GameOptionsMixin {

    @Inject(method = "getCameraType", at = @At("HEAD"), cancellable = true)
    private void mse$overridePerspective(CallbackInfoReturnable<CameraType> cir) {
        if (((ScreenshotTaskHolder) Minecraft.getInstance()).mse$getCurrentScreenshotEntryTask() != null) {
            cir.setReturnValue(CameraType.FIRST_PERSON);
        }
    }
    
}
