package io.github.nickacpt.minecraftscreenshotendpoint

import io.github.nickacpt.minecraftscreenshotendpoint.controllers.screenshotRoute
import io.github.nickacpt.minecraftscreenshotendpoint.controllers.teleportRoute
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.Jetty
import io.ktor.server.plugins.cors.routing.CORS
import net.fabricmc.api.ClientModInitializer
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
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        anyHost()
    }

    screenshotRoute()
    teleportRoute()
}