package io.github.nickacpt.minecraftscreenshotendpoint.controllers

import com.mojang.blaze3d.systems.RenderSystem
import io.github.nickacpt.minecraftscreenshotendpoint.FovOverwritable
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.serde.annotation.SerdeImport
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.annotation.Serdeable.Deserializable
import kotlinx.coroutines.future.await
import net.minecraft.client.MinecraftClient
import net.minecraft.client.MinecraftClient.IS_SYSTEM_MAC
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.recipebook.ClientRecipeBook
import net.minecraft.client.util.ScreenshotRecorder
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.stat.StatHandler
import java.nio.file.Files
import java.util.*
import java.util.concurrent.CompletableFuture

@Introspected
@Serdeable
data class ScreenshotData(
        val x: Double,
        val y: Double,
        val z: Double,
        val pitch: Float,
        val yaw: Float,
        val width: Int,
        val height: Int,
        val fov: Double
) {
    init {
        check(width > 0) { "Width must be greater than 0" }
        check(height > 0) { "Height must be greater than 0" }
        check(fov > 0) { "Fov must be greater than 0" }
    }
}

@Controller("/screenshot")
class ScreenshotController {

    @Get("/{?data*}", produces = [MediaType.IMAGE_PNG])
    suspend fun getScreenshot(@QueryValue data: ScreenshotData): ByteArray {


        if (MinecraftClient.getInstance()?.world == null) {
            throw IllegalStateException("World is null")
        }

        val future = CompletableFuture<ByteArray>()

        RenderSystem.recordRenderCall {
            MinecraftClient.getInstance().takeScreenshot(
                    data.x,
                    data.y,
                    data.z,
                    data.pitch,
                    data.yaw,
                    data.width,
                    data.height,
                    data.fov
            ).let {
                future.complete(it)
            }
        }

        return future.await()
    }

    private fun MinecraftClient.takeScreenshot(x: Double, y: Double, z: Double, pitch: Float, yaw: Float, width: Int, height: Int, fov: Double): ByteArray {
        val framebufferWidth = window.framebufferWidth
        val framebufferHeight = window.framebufferHeight
        val oldFramebuffer = framebuffer

        val framebuffer = SimpleFramebuffer(width, height, true, IS_SYSTEM_MAC)

        gameRenderer.setBlockOutlineEnabled(false)

        worldRenderer.reloadTransparencyPostProcessor()
        window.framebufferWidth = width
        window.framebufferHeight = height

        val oldCameraEntity = cameraEntity

        val newCameraEntity = interactionManager!!.createPlayer(this.world, StatHandler(), ClientRecipeBook())
        newCameraEntity.setPos(x, y, z)
        newCameraEntity.yaw = yaw
        newCameraEntity.pitch = pitch
        cameraEntity = newCameraEntity

        val fovOverwritable = gameRenderer as FovOverwritable
        fovOverwritable.fovOverwrite = fov

        framebuffer.beginWrite(true)
        gameRenderer.renderWorld(1.0f, 0L, MatrixStack())

        val result = ScreenshotRecorder.takeScreenshot(framebuffer).use { img ->
            Files.createTempFile("screenshot", ".png").let {
                img.writeTo(it)
                val bytes = Files.readAllBytes(it)

                Files.deleteIfExists(it)
                return@let bytes
            }
        }

        // Reset everything

        fovOverwritable.fovOverwrite = null

        setCameraEntity(oldCameraEntity)
        gameRenderer.setBlockOutlineEnabled(true)
        window.framebufferWidth = framebufferWidth
        window.framebufferHeight = framebufferHeight
        framebuffer.delete()

        this.worldRenderer.reloadTransparencyPostProcessor()
        oldFramebuffer.beginWrite(true)

        return result
    }

    /*
    public Text takePanorama(File directory, int width, int height) {
        int i = this.window.getFramebufferWidth();
        int j = this.window.getFramebufferHeight();
        SimpleFramebuffer framebuffer = new SimpleFramebuffer(width, height, true, IS_SYSTEM_MAC);
        float f = this.player.getPitch();
        float g = this.player.getYaw();
        float h = this.player.prevPitch;
        float k = this.player.prevYaw;
        this.gameRenderer.setBlockOutlineEnabled(false);
        try {
            this.gameRenderer.setRenderingPanorama(true);
            this.worldRenderer.reloadTransparencyPostProcessor();
            this.window.setFramebufferWidth(width);
            this.window.setFramebufferHeight(height);
            for (int l = 0; l < 6; ++l) {
                switch (l) {
                    case 0: {
                        this.player.setYaw(g);
                        this.player.setPitch(0.0f);
                        break;
                    }
                    case 1: {
                        this.player.setYaw((g + 90.0f) % 360.0f);
                        this.player.setPitch(0.0f);
                        break;
                    }
                    case 2: {
                        this.player.setYaw((g + 180.0f) % 360.0f);
                        this.player.setPitch(0.0f);
                        break;
                    }
                    case 3: {
                        this.player.setYaw((g - 90.0f) % 360.0f);
                        this.player.setPitch(0.0f);
                        break;
                    }
                    case 4: {
                        this.player.setYaw(g);
                        this.player.setPitch(-90.0f);
                        break;
                    }
                    default: {
                        this.player.setYaw(g);
                        this.player.setPitch(90.0f);
                    }
                }
                this.player.prevYaw = this.player.getYaw();
                this.player.prevPitch = this.player.getPitch();
                framebuffer.beginWrite(true);
                this.gameRenderer.renderWorld(1.0f, 0L, new MatrixStack());
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException interruptedException) {
                    // empty catch block
                }
                ScreenshotRecorder.saveScreenshot(directory, "panorama_" + l + ".png", framebuffer, message -> {});
            }
            MutableText text = Text.literal(directory.getName()).formatted(Formatting.UNDERLINE).styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, directory.getAbsolutePath())));
            MutableText mutableText = Text.translatable("screenshot.success", text);
            return mutableText;
        } catch (Exception exception) {
            LOGGER.error("Couldn't save image", exception);
            MutableText mutableText = Text.translatable("screenshot.failure", exception.getMessage());
            return mutableText;
        } finally {
            this.player.setPitch(f);
            this.player.setYaw(g);
            this.player.prevPitch = h;
            this.player.prevYaw = k;
            this.gameRenderer.setBlockOutlineEnabled(true);
            this.window.setFramebufferWidth(i);
            this.window.setFramebufferHeight(j);
            framebuffer.delete();
            this.gameRenderer.setRenderingPanorama(false);
            this.worldRenderer.reloadTransparencyPostProcessor();
            this.getFramebuffer().beginWrite(true);
        }
    }
     */

}