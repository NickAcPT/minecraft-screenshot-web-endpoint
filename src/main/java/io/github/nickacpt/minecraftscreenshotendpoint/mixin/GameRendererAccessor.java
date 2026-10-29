package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Accessor
    GlobalSettingsUniform getGlobalSettingsUniform();

    @Accessor
    FogRenderer getFogRenderer();
}
