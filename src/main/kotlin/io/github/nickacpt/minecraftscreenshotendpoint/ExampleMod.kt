package io.github.nickacpt.minecraftscreenshotendpoint

import io.micronaut.runtime.Micronaut
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

fun main(args: Array<String>) {
	Micronaut.run(*args)
}

object ExampleMod : ModInitializer {
    private val logger = LoggerFactory.getLogger("minecraft-screenshot-web-endpoint")

	override fun onInitialize() {
		thread {
			Micronaut.run()
		}
	}
}