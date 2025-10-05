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
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.util.ScreenshotRecorder
import java.io.ByteArrayOutputStream
import java.nio.channels.Channels
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.concurrent.thread

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

        val max = RenderSystem.getDevice().maxTextureSize

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
    MinecraftClient.getInstance().execute {
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

    return takeScreenshot(entry)
}

suspend fun takeScreenshot(entry: ScreenshotQueueEntry): ByteArray {
    val framebuffer = entry.framebuffer

    val future = CompletableFuture<ByteArray>()

    withContext(renderCallDispatcher) {
        ScreenshotRecorder.takeScreenshot(framebuffer) { nativeImage ->
            thread(start = true) {
                val byteArr = ByteArrayOutputStream()
                Channels.newChannel(byteArr).use { channel ->
                    nativeImage.write(channel)
                }

                nativeImage.close()

                future.complete(byteArr.use { it.toByteArray() } )
            }
        }
    }

    return future.await()
}


