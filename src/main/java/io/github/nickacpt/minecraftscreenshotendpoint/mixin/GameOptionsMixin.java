package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {

    @Inject(method = "getPerspective", at = @At("HEAD"), cancellable = true)
    private void mse$overridePerspective(CallbackInfoReturnable<Perspective> cir) {
        if (MinecraftClient.getInstance().gameRenderer.isRenderingPanorama()) {
            cir.setReturnValue(Perspective.FIRST_PERSON);
        }
    }
    
}
