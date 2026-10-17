package io.github.nickacpt.minecraftscreenshotendpoint.controllers

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey

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

            val player = Minecraft.getInstance().player ?: return@get call.respondText("Player not found")

            Minecraft.getInstance().execute {
                val teleporter = Minecraft.getInstance().singleplayerServer?.let { server ->
                    val world = Identifier.tryParse(level)?.let { id ->
                        server.getLevel(ResourceKey.create(Registries.DIMENSION, id))
                    };

                    {
                        val playerEntity = server.playerList.getPlayer(player.uuid)

                        playerEntity?.also {
                            if (it.abilities.mayfly) it.abilities.flying = true
                        }?.onUpdateAbilities()

                        if (world != null) playerEntity?.teleportTo(world, x, y, z, emptySet(), yaw, pitch, false)

                        Unit
                    }
                };

                if (teleporter != null) {
                    Minecraft.getInstance().singleplayerServer?.executeIfPossible(teleporter)
                } else {
                    player.connection?.sendCommand(
                        "execute in $level run tp ${player.uuid} $x $y $z $yaw $pitch",
                    )
                }
            }

            call.respondText("OK")
        }
    }
}