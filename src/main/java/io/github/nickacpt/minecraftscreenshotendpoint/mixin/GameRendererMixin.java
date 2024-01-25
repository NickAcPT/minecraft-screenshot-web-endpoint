package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.FovOverwritable;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin implements FovOverwritable {
	@Unique
	private Double mse$fovOverwrite;

	@Inject(at = @At("HEAD"), method = "getFov", cancellable = true)
	private void getFovHook(CallbackInfoReturnable<Double> info) {
		if (mse$fovOverwrite != null) {
			info.setReturnValue(mse$fovOverwrite);
		}
	}

	@Override
	public Double mse$getFovOverwrite() {
		return mse$fovOverwrite;
	}

	@Override
	public void mse$setFovOverwrite(Double fovOverwrite) {
		this.mse$fovOverwrite = fovOverwrite;
	}
}