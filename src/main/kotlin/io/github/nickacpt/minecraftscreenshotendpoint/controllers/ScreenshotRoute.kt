package io.github.nickacpt.minecraftscreenshotendpoint.controllers

import com.mojang.blaze3d.systems.RenderSystem
import io.github.nickacpt.minecraftscreenshotendpoint.FovOverwritable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.minecraft.client.MinecraftClient
import net.minecraft.client.MinecraftClient.IS_SYSTEM_MAC
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.recipebook.ClientRecipeBook
import net.minecraft.client.util.ScreenshotRecorder
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.stat.StatHandler
import java.nio.file.Files
import java.util.concurrent.Executor

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

        val max = RenderSystem.maxSupportedTextureSize()

        check(width <= max) { "Width must be less than or equal to $max" }
        check(height <= max) { "Height must be less than or equal to $max" }
    }
}


fun Application.screenshotRoute() {
    val semaphore = Semaphore(1)

    routing {
        get("/screenshot/") {
            runCatching {
                val query = call.request.queryParameters
                val data = ScreenshotData(
                        query["x"]?.toDouble() ?: 0.0,
                        query["y"]?.toDouble() ?: 0.0,
                        query["z"]?.toDouble() ?: 0.0,
                        query["pitch"]?.toFloat() ?: 0.0f,
                        query["yaw"]?.toFloat() ?: 0.0f,
                        query["width"]?.toInt() ?: 0,
                        query["height"]?.toInt() ?: 0,
                        query["fov"]?.toDouble() ?: 0.0
                )

                call.respondBytes {
                    semaphore.withPermit {
                        getScreenshot(data)
                    }
                }
            }.onFailure {
                call.response.status(HttpStatusCode.InternalServerError)
                call.respond(it.message ?: "Unknown error")
                it.printStackTrace()
            }
        }
    }

}

val renderCallDispatcher = Executor {
    RenderSystem.recordRenderCall {
        it.run()
    }
}.asCoroutineDispatcher()

private suspend fun getScreenshot(data: ScreenshotData): ByteArray {
    if (MinecraftClient.getInstance()?.world == null) {
        throw IllegalStateException("World is null")
    }

    return withContext(renderCallDispatcher) {
        MinecraftClient.getInstance().takeScreenshot(
                data.x,
                data.y,
                data.z,
                data.pitch,
                data.yaw,
                data.width,
                data.height,
                data.fov
        )
    }
}


private suspend fun MinecraftClient.takeScreenshot(x: Double, y: Double, z: Double, pitch: Float, yaw: Float, width: Int, height: Int, fov: Double): ByteArray {
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

    framebuffer.beginWrite(false)
    gameRenderer.renderWorld(1.0f, 0L, MatrixStack())

    val img = ScreenshotRecorder.takeScreenshot(framebuffer)

    val result = withContext(Dispatchers.IO) {
        Files.createTempFile("screenshot", ".png").let {
            img.writeTo(it)
            val bytes = Files.readAllBytes(it)

            Files.deleteIfExists(it)
            return@let bytes
        }
    }

    img.close()

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
