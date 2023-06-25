package io.github.nickacpt.minecraftscreenshotendpoint

import io.github.nickacpt.minecraftscreenshotendpoint.controllers.screenshotRoute
import net.fabricmc.api.ModInitializer
import kotlin.concurrent.thread

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*

object MinecraftScreenshotEndpointMod : ModInitializer {
	override fun onInitialize() {
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
}