package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.FovOverwritable;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin implements FovOverwritable {
	private Double fovOverwrite;

	@Inject(at = @At("HEAD"), method = "getFov", cancellable = true)
	private void getFovHook(CallbackInfoReturnable<Double> info) {
		if (fovOverwrite != null) {
			info.setReturnValue(fovOverwrite);
		}
	}

	@Override
	public Double getFovOverwrite() {
		return fovOverwrite;
	}

	@Override
	public void setFovOverwrite(Double fovOverwrite) {
		this.fovOverwrite = fovOverwrite;
	}
}