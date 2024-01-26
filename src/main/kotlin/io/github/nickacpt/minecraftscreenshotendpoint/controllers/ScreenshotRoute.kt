package io.github.nickacpt.minecraftscreenshotendpoint.controllers

import com.mojang.blaze3d.systems.RenderSystem
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueue
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueueEntry
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
import net.minecraft.client.gl.Framebuffer
import net.minecraft.client.texture.NativeImage
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

    val entry = withContext(renderCallDispatcher) {
        ScreenshotQueueEntry(data)
    }
    ScreenshotQueue.addEntry(entry)

    entry.await()

    return takeScreenshot(entry.framebuffer)
}

suspend fun takeScreenshot(framebuffer: Framebuffer): ByteArray {
    val w = framebuffer.textureWidth
    val h = framebuffer.textureHeight
    val nativeImage = NativeImage(w, h, false)

    println("Taking screenshot with size $w x $h on framebuffer $framebuffer")

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


