package io.github.nickacpt.minecraftscreenshotendpoint.mixin;

import io.github.nickacpt.minecraftscreenshotendpoint.render.ScreenshotBlockOverrides;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Swaps the draw list of the terrain pass for block override screenshots.
 *
 * The override sections are compiled by a private dispatcher, so the regular {@code visibleSections} list and the
 * regular section meshes must stay untouched: the occlusion graph rebuilds that list every frame. Instead,
 * {@link ScreenshotBlockOverrides} is consulted only while an override session is active, and only inside
 * {@code extractSectionDrawGroups}, the single place where the terrain draw list is built. Outside a session every
 * redirect returns exactly what the original code would have read.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    /** Adds the override sections the occlusion graph did not select, so they are drawn regardless of culling. */
    @Redirect(
        method = "extractSectionDrawGroups",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;visibleSections:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            opcode = Opcodes.GETFIELD
        )
    )
    private static ObjectArrayList<SectionRenderDispatcher.RenderSection> mse$visibleSections(LevelRenderer levelRenderer) {
        return ScreenshotBlockOverrides.visibleSections(levelRenderer.visibleSections());
    }

    /** Uses the override mesh for an overridden section, so its terrain comes from the override compile. */
    @Redirect(
        method = "extractSectionDrawGroups",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;getSectionMesh()Lnet/minecraft/client/renderer/chunk/SectionMesh;"
        )
    )
    private static SectionMesh mse$sectionMesh(SectionRenderDispatcher.RenderSection section) {
        return ScreenshotBlockOverrides.sectionMesh(section);
    }

    /** Resolves override meshes in the override dispatcher's buffers; regular meshes keep the regular lookup. */
    @Redirect(
        method = "extractSectionDrawGroups",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;getRenderSectionSlice(Lnet/minecraft/client/renderer/chunk/SectionMesh;Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSectionBufferSlice;"
        )
    )
    private static SectionRenderDispatcher.RenderSectionBufferSlice mse$renderSectionSlice(
        SectionRenderDispatcher dispatcher,
        SectionMesh mesh,
        ChunkSectionLayer layer
    ) {
        return ScreenshotBlockOverrides.renderSectionSlice(dispatcher, mesh, layer);
    }
}
