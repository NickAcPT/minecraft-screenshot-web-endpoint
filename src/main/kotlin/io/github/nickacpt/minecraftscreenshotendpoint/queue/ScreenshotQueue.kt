package io.github.nickacpt.minecraftscreenshotendpoint.queue

import io.github.nickacpt.minecraftscreenshotendpoint.mixin.MinecraftClientMixin
import net.minecraft.client.MinecraftClient
import java.util.concurrent.ConcurrentLinkedDeque

object ScreenshotQueue {
    val size: Int get() = queue.size
    private val queue = ConcurrentLinkedDeque<ScreenshotQueueEntry>()

    var skipNextFrameFlip = false

    fun addEntry(entry: ScreenshotQueueEntry) {
        queue.add(entry)
    }

    fun getNextEntry(): ScreenshotQueueEntry? {
        return queue.poll().also { it: ScreenshotQueueEntry? ->
            val isTakingScreenshot = it != null

            skipNextFrameFlip = isTakingScreenshot
            MinecraftClient.getInstance().gameRenderer?.apply {
                isRenderingPanorama = isTakingScreenshot
            }
        }
    }
}