package io.github.nickacpt.minecraftscreenshotendpoint.controllers

import com.mojang.blaze3d.systems.RenderSystem
import io.github.nickacpt.minecraftscreenshotendpoint.FovOverwritable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.MinecraftClient.IS_SYSTEM_MAC
import net.minecraft.client.gl.Framebuffer
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.recipebook.ClientRecipeBook
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.stat.StatHandler
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
                        withContext(ioDispatcher) {
                            getScreenshot(data)
                        }
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

val ioDispatcher = Executor {
    MinecraftClient.getInstance().execute(it)
}.asCoroutineDispatcher()

val renderCallDispatcher = Executor {
    RenderSystem.recordRenderCall {
        it.run()
    }
}.asCoroutineDispatcher()

private suspend fun getScreenshot(data: ScreenshotData): ByteArray {
    if (MinecraftClient.getInstance()?.world == null) {
        throw IllegalStateException("World is null")
    }

    return MinecraftClient.getInstance().takeScreenshot(
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


suspend fun takeScreenshot(framebuffer: Framebuffer, oldFramebuffer: Framebuffer): NativeImage {
    val w = framebuffer.textureWidth
    val h = framebuffer.textureHeight
    val nativeImage = NativeImage(w, h, false)

    withContext(renderCallDispatcher) {
        RenderSystem.bindTexture(framebuffer.colorAttachment)
        nativeImage.loadFromTextureImage(0, true)

        oldFramebuffer.beginWrite(true)
    }

    nativeImage.mirrorVertically()
    return nativeImage
}


private suspend fun MinecraftClient.takeScreenshot(x: Double, y: Double, z: Double, pitch: Float, yaw: Float, width: Int, height: Int, fov: Double): ByteArray {
    val oldFramebuffer = framebuffer

    gameRenderer.setBlockOutlineEnabled(false)
    worldRenderer.reloadTransparencyPostProcessor()

    val oldCameraEntity = cameraEntity

    val newCameraEntity = interactionManager!!.createPlayer(this.world, StatHandler(), ClientRecipeBook())
    newCameraEntity.setPos(x, y, z)
    newCameraEntity.yaw = yaw
    newCameraEntity.pitch = pitch
    cameraEntity = newCameraEntity

    val fovOverwritable = gameRenderer as FovOverwritable
    fovOverwritable.fovOverwrite = fov

    val framebuffer = withContext(renderCallDispatcher) {
        SimpleFramebuffer(width, height, true, IS_SYSTEM_MAC).also {
            it.beginWrite(false)
            gameRenderer.renderWorld(1.0f, 0L, MatrixStack())
        }
    }

    val img = takeScreenshot(framebuffer, oldFramebuffer)

    // Reset everything
    fovOverwritable.fovOverwrite = null

    setCameraEntity(oldCameraEntity)
    gameRenderer.setBlockOutlineEnabled(true)

    this.worldRenderer.reloadTransparencyPostProcessor()

    val result = img.bytes
    img.close()

    withContext(renderCallDispatcher) {
        framebuffer.delete()
    }

    return result
}