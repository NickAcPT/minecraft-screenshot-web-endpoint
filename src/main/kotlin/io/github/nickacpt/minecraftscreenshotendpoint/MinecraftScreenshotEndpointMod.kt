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
    private val serverLock = Any()
    private var server: EmbeddedServer<*, *>? = null
    private var stopping = false

    override fun onInitializeClient() {
        thread(isDaemon = true) {
            initializeKtor()
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        createServer().start(wait = true)
    }

    private fun initializeKtor() {
        synchronized(serverLock) {
            // Serialize startup and shutdown so closing early cannot leave a late-starting server.
            if (stopping) return
            server = createServer().start(wait = false)
        }
    }

    /**
     * Stops Jetty before Minecraft exits; its scheduler owns a non-daemon thread.
     */
    @JvmStatic
    fun stopServer() {
        synchronized(serverLock) {
            stopping = true
            server?.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
            server = null
        }
    }

    private fun createServer() =
        embeddedServer(Jetty, configure = {
            connector {
                host = "127.0.0.1"
                port = 36210
            }
        }, module = Application::module)
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