package io.github.nickacpt.minecraftscreenshotendpoint.queue

import io.github.nickacpt.minecraftscreenshotendpoint.controllers.ScreenshotData
import kotlinx.coroutines.future.await
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.recipebook.ClientRecipeBook
import net.minecraft.stat.StatHandler
import java.util.UUID
import java.util.concurrent.CompletableFuture

data class ScreenshotQueueEntry(
    val settings: ScreenshotData,
    val framebuffer: SimpleFramebuffer,
    val cameraEntity: ClientPlayerEntity,
    val future: CompletableFuture<Unit>,
    val id: UUID = UUID.randomUUID(),
) {
    suspend fun await() {
        return future.await()
    }

    fun notifyDone() {
        future.complete(Unit)
    }

    constructor(
        settings: ScreenshotData,
    ) : this(settings, createFramebuffer(settings), createCameraEntity(settings), CompletableFuture())
}

private fun createFramebuffer(settings: ScreenshotData) =
    SimpleFramebuffer(settings.width, settings.height, true, MinecraftClient.IS_SYSTEM_MAC)

private fun createCameraEntity(settings: ScreenshotData): ClientPlayerEntity {
    val newCameraEntity = MinecraftClient.getInstance().run {
        this.interactionManager!!.createPlayer(this.world, StatHandler(), ClientRecipeBook())
    }

    newCameraEntity.setPos(settings.x, settings.y, settings.z)
    newCameraEntity.yaw = settings.yaw
    newCameraEntity.pitch = settings.pitch

    newCameraEntity.prevYaw = settings.yaw
    newCameraEntity.prevPitch = settings.pitch
    newCameraEntity.prevX = settings.x
    newCameraEntity.prevY = settings.y
    newCameraEntity.prevZ = settings.z

    newCameraEntity.kill()

    return newCameraEntity
}