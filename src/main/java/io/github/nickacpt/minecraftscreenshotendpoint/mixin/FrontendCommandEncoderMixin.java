package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import io.github.nickacpt.minecraftscreenshotendpoint.render.ScreenshotStencil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Limits every render pass that draws into the screenshot's main target to the stencil bounds.
 * <p>
 * The render area of a pass is also its scissor box in the backend, so restricting it here clips GPU draws without
 * touching the level renderer or any draw call. The hook sits on the backend-agnostic frontend encoder, which every
 * pass goes through, so it works for both the GL and Vulkan backends. The pass is only changed while a stencil is
 * active, so regular frames are unaffected.
 */
@Mixin(FrontendCommandEncoder.class)
public abstract class FrontendCommandEncoderMixin {
    @ModifyVariable(
        method = "createRenderPass(Lcom/mojang/renderpearl/api/commands/RenderPassDescriptor;)Lcom/mojang/renderpearl/api/commands/RenderPass;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private RenderPassDescriptor mse$restrictToStencil(RenderPassDescriptor descriptor) {
        return ScreenshotStencil.restrict(descriptor);
    }
}
