package io.github.nickacpt.minecraftscreenshotendpoint.queue

import io.github.nickacpt.minecraftscreenshotendpoint.controllers.ScreenshotData
import kotlinx.coroutines.future.await
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.renderpearl.api.GpuFormat
import net.minecraft.client.ClientRecipeBook
import net.minecraft.client.Minecraft
import net.minecraft.client.player.ItemActivation
import net.minecraft.client.player.LocalPlayer
import net.minecraft.stats.StatsCounter
import java.util.UUID
import java.util.concurrent.CompletableFuture

data class ScreenshotQueueEntry(
    val settings: ScreenshotData,
    val framebuffer: TextureTarget,
    val cameraEntity: LocalPlayer,
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
    TextureTarget("Screenshot", settings.width, settings.height, GpuFormat.RGBA8_UNORM, null)

private fun createCameraEntity(settings: ScreenshotData): LocalPlayer {
    val newCameraEntity = Minecraft.getInstance().run {
        this.gameMode!!.createPlayer(this.level!!, StatsCounter(), ClientRecipeBook(), ItemActivation())
    }

    newCameraEntity.setPos(settings.x, settings.y, settings.z)
    newCameraEntity.yRot = settings.yaw
    newCameraEntity.xRot = settings.pitch

    newCameraEntity.yRotO = settings.yaw
    newCameraEntity.xRotO = settings.pitch
    newCameraEntity.xo = settings.x
    newCameraEntity.yo = settings.y
    newCameraEntity.zo = settings.z

    newCameraEntity.setNoGravity(true)

    //newCameraEntity.kill(Minecraft.getInstance().singleplayerServer?.overworld())

    //newCameraEntity.kill(Minecraft.)

    return newCameraEntity
}