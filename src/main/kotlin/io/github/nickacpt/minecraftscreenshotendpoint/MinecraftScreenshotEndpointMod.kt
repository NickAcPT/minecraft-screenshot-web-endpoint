package io.github.nickacpt.minecraftscreenshotendpoint

import io.github.nickacpt.minecraftscreenshotendpoint.controllers.screenshotRoute
import io.github.nickacpt.minecraftscreenshotendpoint.controllers.teleportRoute
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.MinecraftClient
import kotlin.concurrent.thread

object MinecraftScreenshotEndpointMod : ClientModInitializer {
	override fun onInitializeClient() {
		thread {
			initializeKtor()
		}
	}

	@JvmStatic
	fun main(args: Array<String>) {
		initializeKtor()
	}

	private fun initializeKtor() {
		embeddedServer(Jetty, port = 36210, host = "127.0.0.1", module = Application::module)
			.start(wait = true)
	}
}

fun Application.module() {
	screenshotRoute()
	teleportRoute()
}