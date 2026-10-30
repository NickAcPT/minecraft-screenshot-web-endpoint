package io.github.nickacpt.minecraftscreenshotendpoint.controllers

import com.mojang.blaze3d.systems.RenderSystem
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueue
import io.github.nickacpt.minecraftscreenshotendpoint.queue.ScreenshotQueueEntry
import io.github.nickacpt.minecraftscreenshotendpoint.render.parseBlockOverrides
import io.github.nickacpt.minecraftscreenshotendpoint.render.ScreenshotStencil
import io.github.nickacpt.minecraftscreenshotendpoint.render.StencilRect
import io.github.nickacpt.minecraftscreenshotendpoint.render.parseStencils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.world.level.block.state.BlockState
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
    val fov: Double,
    /** Render-only block state overrides for this screenshot, keyed by `BlockPos.asLong`. See [screenshotRoute]. */
    val blockOverrides: Long2ObjectMap<BlockState> = Long2ObjectMaps.emptyMap(),
    /** Stencil rects in screenshot pixels, already clamped to the image. Empty means no stencil. See [screenshotRoute]. */
    val stencils: List<StencilRect> = emptyList(),
) {
    init {
        check(width > 0) { "Width must be greater than 0" }
        check(height > 0) { "Height must be greater than 0" }
        check(fov > 0) { "Fov must be greater than 0" }

        val max = RenderSystem.getDevice().deviceInfo.limits().maxTextureSize()

        check(width <= max) { "Width must be less than or equal to $max" }
        check(height <= max) { "Height must be less than or equal to $max" }
    }
}

/** A request failure with the HTTP status to report; its message is returned to the client as-is. */
class ScreenshotRequestException(val status: HttpStatusCode, message: String) : RuntimeException(message)


/**
 * `GET /screenshot/` renders one PNG from the camera at `x,y,z` (`yaw`, `pitch`, vertical `fov`) at `width` x `height`.
 *
 * `block` (repeatable) renders block states in this screenshot only; the world is never changed:
 * - `block=x,y,z,<blockstate>` overrides one position.
 * - `block=x1,y1,z1,x2,y2,z2,<blockstate>` overrides the inclusive box between two corners (any corner order).
 * - `<blockstate>` is command syntax (`stone`, `minecraft:oak_log[axis=y]`, `air` to hide a block) and may contain commas.
 * Later values win on overlap. At most [MAX_BLOCK_OVERRIDE_POSITIONS] positions in total (box volumes summed) and
 * [MAX_BLOCK_OVERRIDE_SECTIONS] affected chunk sections are allowed, otherwise 400. Only sections in the render view
 * area are drawn with overrides. Block entity renderers of the real world still draw at overridden positions, and an
 * override whose block needs a block entity renderer (chest, sign, ...) is drawn without it. Returns 501 while Sodium is loaded.
 *
 * `stencil=x,y,w,h` (repeatable) restricts rendering to the union of the rects; the image keeps its full size and camera
 * projection, and pixels outside every rect are fully transparent. Rects are clamped to the image; a malformed value, or
 * rects that do not overlap the image at all, return 400. GPU work is limited to the bounding box of the rects, so
 * pixels inside that box but outside every rect are still drawn and then made transparent. Without `stencil` nothing changes.
 *
 * Errors: 400 for malformed parameters or exceeded caps, 501 for unsupported features, 500 otherwise.
 */
fun Application.screenshotRoute() {
    val semaphore = Semaphore(1)

    routing {
        get("/screenshot/") {
            runCatching {
                val query = call.request.queryParameters
                val width = query["width"]?.toInt() ?: 0
                val height = query["height"]?.toInt() ?: 0
                val data = ScreenshotData(
                    query["x"]?.toDouble() ?: 0.0,
                    query["y"]?.toDouble() ?: 0.0,
                    query["z"]?.toDouble() ?: 0.0,
                    query["pitch"]?.toFloat() ?: 0.0f,
                    query["yaw"]?.toFloat() ?: 0.0f,
                    width,
                    height,
                    query["fov"]?.toDouble() ?: 0.0,
                    blockOverrides = parseBlockOverrides(query.getAll("block").orEmpty()),
                    stencils = parseStencils(query.getAll("stencil").orEmpty(), width, height),
                )

                call.respondBytes {
                    semaphore.withPermit {
                        withContext(ioDispatcher) {
                            getScreenshot(data)
                        }
                    }
                }
            }.onFailure {
                val status = (it as? ScreenshotRequestException)?.status ?: HttpStatusCode.InternalServerError
                call.response.status(status)
                call.respond(if (it is ScreenshotRequestException) it.message.orEmpty() else it.stackTraceToString())
            }
        }
    }

}

val ioDispatcher = Dispatchers.IO

val renderCallDispatcher = Executor {
    Minecraft.getInstance().execute {
        it.run()
    }
}.asCoroutineDispatcher()

private suspend fun getScreenshot(data: ScreenshotData): ByteArray {
    if (Minecraft.getInstance()?.level == null) {
        throw IllegalStateException("World is null")
    }
    if (Minecraft.getInstance()?.player == null) {
        throw IllegalStateException("Player is null")
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
        Screenshot.takeScreenshot(framebuffer) { nativeImage ->
            thread(start = true) {
                if (entry.settings.stencils.isNotEmpty()) {
                    ScreenshotStencil.applyAlpha(nativeImage, entry.settings.stencils)
                }
                val byteArr = ByteArrayOutputStream()
                Channels.newChannel(byteArr).use { channel ->
                    nativeImage.writeToChannel(channel)
                }

                nativeImage.close()

                future.complete(byteArr.use { it.toByteArray() } )
            }
        }
    }

    return future.await()
}


