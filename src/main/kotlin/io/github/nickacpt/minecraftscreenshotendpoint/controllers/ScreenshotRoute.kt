package io.github.nickacpt.minecraftscreenshotendpoint.controllers

import com.mojang.blaze3d.systems.RenderSystem
import io.github.nickacpt.minecraftscreenshotendpoint.FovOverwritable
import io.github.nickacpt.minecraftscreenshotendpoint.FramebufferOverwritable
import io.github.nickacpt.minecraftscreenshotendpoint.FramebufferSizeOverwritable
import io.github.nickacpt.minecraftscreenshotendpoint.OriginalWindowFramebufferSize
import io.github.nickacpt.minecraftscreenshotendpoint.mixin.WindowAccessor
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
import kotlin.math.roundToInt

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
                call.respond(it.stackTraceToString())
            }
        }
    }

}

val ioDispatcher = Dispatchers.IO

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


suspend fun takeScreenshot(framebuffer: Framebuffer): ByteArray {
    val w = framebuffer.textureWidth
    val h = framebuffer.textureHeight
    val nativeImage = NativeImage(w, h, false)

    withContext(renderCallDispatcher) {
        RenderSystem.bindTexture(framebuffer.colorAttachment)
        nativeImage.loadFromTextureImage(0, true)
        framebuffer.delete()
    }

    nativeImage.mirrorVertically()

    return nativeImage.use {
        it.bytes
    }
}


@Suppress("CAST_NEVER_SUCCEEDS") // We know it's not null because we're we mixed into it
private suspend fun MinecraftClient.takeScreenshot(
    x: Double,
    y: Double,
    z: Double,
    pitch: Float,
    yaw: Float,
    width: Int,
    height: Int,
    fov: Double
): ByteArray {
    val newCameraEntity = interactionManager!!.createPlayer(this.world, StatHandler(), ClientRecipeBook())
    newCameraEntity.setPos(x, y, z)
    newCameraEntity.yaw = yaw
    newCameraEntity.pitch = pitch
    newCameraEntity.prevYaw = yaw
    newCameraEntity.prevPitch = pitch

    val fovOverwritable = gameRenderer as FovOverwritable
    val windowOverwritable = window as FramebufferSizeOverwritable

    @Suppress("CAST_NEVER_SUCCEEDS") // We know it's not null because we're we mixed into it
    val framebuffer = withContext(renderCallDispatcher) {
        println("Taking screenshot ${framebuffer.useDepthAttachment}")
        SimpleFramebuffer(width, height, true, IS_SYSTEM_MAC).also {
            val oldCameraEntity = cameraEntity
            println("Taking screenshot 2 ${it.useDepthAttachment}")

            gameRenderer.isRenderingPanorama = true
            val original = window as? OriginalWindowFramebufferSize
            val oldBufferWidth = original?.`mse$originalFramebufferWidth`() ?: window.framebufferWidth
            val oldBufferHeight = original?.`mse$originalFramebufferHeight`() ?: window.framebufferHeight

            // Difference between the old and new buffer sizes as a percentage
            // (should be 1 unless there is a mod changing the return of the framebuffer size getters)
            val widthDifference = oldBufferWidth.toFloat() / window.framebufferWidth
            val heightDifference = oldBufferHeight.toFloat() / window.framebufferHeight

            // Prepare our screenshot camera
            windowOverwritable.`mse$setFramebufferWidth`((width * widthDifference).roundToInt())
            windowOverwritable.`mse$setFramebufferHeight`((height * heightDifference).roundToInt())

            cameraEntity = newCameraEntity
            fovOverwritable.`mse$setFovOverwrite`(fov)

            gameRenderer.setBlockOutlineEnabled(false)
            worldRenderer.reloadTransparencyPostProcessor()

            val overwritable = this@takeScreenshot as FramebufferOverwritable

            overwritable.`mse$setFramebuffer`(it)

            // GO! Render things now!
            it.beginWrite(false)

            gameRenderer.renderWorld(1.0f, 0L, MatrixStack())

            // Reset everything
            fovOverwritable.`mse$setFovOverwrite`(null)

            cameraEntity = oldCameraEntity
            gameRenderer.setBlockOutlineEnabled(true)
            worldRenderer.reloadTransparencyPostProcessor()

            windowOverwritable.`mse$setFramebufferWidth`(null)
            windowOverwritable.`mse$setFramebufferHeight`(null)

            gameRenderer.isRenderingPanorama = false

            overwritable.`mse$setFramebuffer`(null)

            framebuffer.beginWrite(true)

            (window as WindowAccessor).invokeOnFramebufferSizeChanged(window.handle, oldBufferWidth, oldBufferHeight)
        }
    }

    return takeScreenshot(framebuffer).also {
        withContext(renderCallDispatcher) {
            onResolutionChanged()
        }
    }
}
