package io.github.nickacpt.minecraftscreenshotendpoint.queue

import java.util.concurrent.ConcurrentLinkedDeque

object ScreenshotQueue {
    val size: Int get() = queue.size

    private val queue = ConcurrentLinkedDeque<ScreenshotQueueEntry>()

    fun addEntry(entry: ScreenshotQueueEntry) {
        queue.add(entry)
    }

    fun getNextEntry(): ScreenshotQueueEntry? {
        return queue.poll()
    }
}