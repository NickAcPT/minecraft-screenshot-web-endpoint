package io.github.nickacpt.minecraftscreenshotendpoint.mixin.compat;

import io.github.nickacpt.minecraftscreenshotendpoint.ScreenshotTaskHolder;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Axiom's editor UI shrinks the game to its viewport by overriding {@code Window#getWidth}/{@code getHeight}
 * while {@code EditorUI.isActive()} is true. During a screenshot pass that would replace the requested
 * resolution (and projection aspect) with the editor viewport size, so the editor is reported inactive
 * for the duration of the pass only. Pseudo target: applies only when Axiom is installed.
 */
@Pseudo
@Mixin(targets = "com.moulberry.axiom.editor.EditorUI", remap = false)
public abstract class AxiomEditorUIMixin {

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true, require = 0)
    private static void mse$hideEditorDuringScreenshot(CallbackInfoReturnable<Boolean> cir) {
        var holder = (ScreenshotTaskHolder) Minecraft.getInstance();

        if (holder.mse$getCurrentScreenshotEntryTask() != null) {
            cir.setReturnValue(false);
        }
    }
}
