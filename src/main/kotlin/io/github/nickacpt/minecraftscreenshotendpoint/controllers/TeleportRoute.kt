package io.github.nickacpt.minecraftscreenshotendpoint.controllers

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.minecraft.client.MinecraftClient

fun Application.teleportRoute() {
    routing {
        get("/teleport/") {
            val query = call.request.queryParameters

            val x = query["x"]?.toDouble() ?: 0.0
            val y = query["y"]?.toDouble() ?: 0.0
            val z = query["z"]?.toDouble() ?: 0.0
            val pitch = query["pitch"]?.toFloat() ?: 0.0f
            val yaw = query["yaw"]?.toFloat() ?: 0.0f

            val level = query["level"] ?: "minecraft:overworld"

            val player = MinecraftClient.getInstance().player ?: return@get call.respondText("Player not found")

            MinecraftClient.getInstance().execute {
                player.networkHandler?.sendChatCommand(
                    "execute in $level run tp ${player.uuid} $x $y $z $yaw $pitch",
                )
            }

            call.respondText("OK")
        }
    }
}