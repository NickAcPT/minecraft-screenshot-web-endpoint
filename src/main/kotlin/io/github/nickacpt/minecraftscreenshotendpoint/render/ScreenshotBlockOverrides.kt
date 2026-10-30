package io.github.nickacpt.minecraftscreenshotendpoint.render

import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.github.nickacpt.minecraftscreenshotendpoint.controllers.ScreenshotRequestException
import io.github.nickacpt.minecraftscreenshotendpoint.render.sodium.SodiumScreenshotBridge
import io.ktor.http.HttpStatusCode
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.client.renderer.chunk.CompiledSectionMesh
import net.minecraft.client.renderer.chunk.RenderSectionRegion
import net.minecraft.client.renderer.chunk.SectionCompiler
import net.minecraft.client.renderer.chunk.SectionCopy
import net.minecraft.client.renderer.chunk.SectionMesh
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.util.Util
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs

/** Upper bound on the summed volume of all `block` boxes in one screenshot (overlaps counted). */
const val MAX_BLOCK_OVERRIDE_POSITIONS = 4096

/** Upper bound on the chunk sections whose mesh must be recompiled for one screenshot's `block` overrides. */
const val MAX_BLOCK_OVERRIDE_SECTIONS = 512

private const val MAX_COORDINATE = 30_000_000

/**
 * Parses the repeatable `block` query parameter.
 *
 * Each value is either `x,y,z,<blockstate>` or `x1,y1,z1,x2,y2,z2,<blockstate>` (inclusive box, corners may be
 * given in any order). The form is chosen by whether the 4th to 6th tokens are integers; block states may contain
 * commas inside their property brackets. Later values overwrite earlier positions.
 *
 * @throws ScreenshotRequestException 400 for malformed values or exceeded caps
 */
fun parseBlockOverrides(params: List<String>): Long2ObjectMap<BlockState> {
    if (params.isEmpty()) return Long2ObjectOpenHashMap()

    val level = Minecraft.getInstance().level ?: throw IllegalStateException("World is null")
    val blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK)

    val overrides = Long2ObjectOpenHashMap<BlockState>()
    var volume = 0L
    for (param in params) {
        val parts = param.split(',')
        val isBox = parts.size >= 7 && parts.subList(3, 6).all { it.trim().toIntOrNull() != null }
        val coordinateCount = if (isBox) 6 else 3
        if (parts.size <= coordinateCount) {
            throw badRequest("block '$param' must be x,y,z,<blockstate> or x1,y1,z1,x2,y2,z2,<blockstate>")
        }

        val coordinates = IntArray(coordinateCount) { parseCoordinate(parts[it], param) }
        val stateText = parts.drop(coordinateCount).joinToString(",").trim()
        val state = try {
            BlockStateParser.parseForBlock(blocks, stateText, false).blockState()
        } catch (e: CommandSyntaxException) {
            throw badRequest("block '$param' has an invalid block state: ${e.message}")
        }

        val minX = minOf(coordinates[0], coordinates.getOrElse(3) { coordinates[0] })
        val minY = minOf(coordinates[1], coordinates.getOrElse(4) { coordinates[1] })
        val minZ = minOf(coordinates[2], coordinates.getOrElse(5) { coordinates[2] })
        val maxX = maxOf(coordinates[0], coordinates.getOrElse(3) { coordinates[0] })
        val maxY = maxOf(coordinates[1], coordinates.getOrElse(4) { coordinates[1] })
        val maxZ = maxOf(coordinates[2], coordinates.getOrElse(5) { coordinates[2] })

        volume += (maxX - minX + 1).toLong() * (maxY - minY + 1) * (maxZ - minZ + 1)
        if (volume > MAX_BLOCK_OVERRIDE_POSITIONS) {
            throw badRequest("block overrides cover more than $MAX_BLOCK_OVERRIDE_POSITIONS positions in total")
        }

        for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
            overrides.put(BlockPos.asLong(x, y, z), state)
        }
    }

    val sections = affectedSections(overrides)
    if (sections.size > MAX_BLOCK_OVERRIDE_SECTIONS) {
        throw badRequest("block overrides affect more than $MAX_BLOCK_OVERRIDE_SECTIONS chunk sections")
    }

    return overrides
}

/**
 * Sections whose compiled mesh depends on an overridden position: a section's mesh reads its own blocks and the
 * ring of neighbouring blocks (face culling, ambient occlusion), so every section containing `p + d` for
 * `d` in `{-1, 0, 1}^3` must be recompiled for each overridden `p`.
 */
private fun affectedSections(overrides: Long2ObjectMap<BlockState>): LongOpenHashSet {
    val sections = LongOpenHashSet()
    for (key in overrides.keys.toLongArray()) {
        val x = BlockPos.getX(key)
        val y = BlockPos.getY(key)
        val z = BlockPos.getZ(key)
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
            sections.add(
                SectionPos.asLong(
                    SectionPos.blockToSectionCoord(x + dx),
                    SectionPos.blockToSectionCoord(y + dy),
                    SectionPos.blockToSectionCoord(z + dz)
                )
            )
        }
    }
    return sections
}

private fun parseCoordinate(token: String, param: String): Int {
    val value = token.trim().toIntOrNull()
    if (value == null || abs(value) > MAX_COORDINATE) {
        throw badRequest("block '$param' has an invalid coordinate '$token'")
    }
    return value
}

private fun badRequest(message: String) = ScreenshotRequestException(HttpStatusCode.BadRequest, message)

/**
 * Render-only block overrides for the screenshot pass.
 *
 * Without Sodium, overridden sections are recompiled into a private [SectionRenderDispatcher] whose regions read
 * the overrides, and the level renderer draws those meshes in place of the real ones for the duration of the pass.
 * With Sodium, the terrain renderer is swapped for a private one fed by the overridden chunk data (see
 * [SodiumScreenshotBridge]). In both cases the real
 * [net.minecraft.world.level.Level] data, block entities, lighting, collision and the regular section meshes are
 * never touched, so the regular frames never observe the overrides.
 *
 * Only sections already in the render view area are drawn, matching where the world itself is drawn. Block
 * entity renderers of the real world still draw at overridden positions, and blocks that need a block entity
 * renderer (chests, signs, ...) are not drawn for their override.
 */
object ScreenshotBlockOverrides {
    /** Private compiler and upload path, reused across screenshots. Never ticks; only used on the render thread. */
    private var dispatcher: SectionRenderDispatcher? = null

    /** The override session of the screenshot pass currently rendering, or null. Read by the level renderer mixin. */
    @JvmStatic
    var active: Session? = null
        private set

    /**
     * Compiles and uploads the overridden sections for one screenshot pass. Returns null when there is nothing to
     * override. Must be paired with [end] in a `finally` around the level render.
     */
    @JvmStatic
    fun begin(cameraPos: Vec3, overrides: Long2ObjectMap<BlockState>): AutoCloseable? {
        if (overrides.isEmpty()) return null
        if (FabricLoader.getInstance().isModLoaded("sodium")) return SodiumScreenshotBridge.begin(overrides)
        return beginVanilla(cameraPos, overrides)
    }

    /** Ends a pass started by [begin]. Closing the returned session restores the regular terrain. */
    @JvmStatic
    fun end(session: AutoCloseable?) {
        session?.close()
    }

    private fun beginVanilla(cameraPos: Vec3, overrides: Long2ObjectMap<BlockState>): Session? {

        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return null
        val viewArea = minecraft.levelRenderer.viewArea() ?: return null

        val options = minecraft.options
        val modelManager = minecraft.modelManager
        val compiler = SectionCompiler(
            options.ambientOcclusion().get(),
            options.cutoutLeaves().get(),
            modelManager.blockStateModelSet,
            modelManager.fluidStateModelSet,
            minecraft.blockColors
        )
        val overrideDispatcher = dispatcher ?: SectionRenderDispatcher(
            Util.backgroundExecutor(),
            minecraft.gameRenderer.renderBuffers(),
            compiler,
            { }
        ).also { dispatcher = it }
        overrideDispatcher.setCompiler(compiler)
        overrideDispatcher.setCameraPosition(cameraPos)

        val copies = Long2ObjectOpenHashMap<SectionCopy>()
        val compiled = Long2ObjectOpenHashMap<SectionRenderDispatcher.RenderSection>()
        val regularSections = ArrayList<SectionRenderDispatcher.RenderSection>()
        try {
            for (node in affectedSections(overrides).toLongArray()) {
                val regular = viewArea.getRenderSectionAt(SectionPos.of(node).origin()) ?: continue

                val section = overrideDispatcher.RenderSection(compiled.size, node)
                section.compileSync(createRegion(level, node, overrides, copies))
                var attempts = 0
                while (section.getSectionMesh() === CompiledSectionMesh.UNCOMPILED) {
                    check(attempts++ < MAX_UPLOAD_ATTEMPTS) { "Override section $node never finished uploading" }
                    overrideDispatcher.uploadTerrainBuffersToGpu()
                }

                compiled.put(node, section)
                regularSections.add(regular)
            }
        } catch (t: Throwable) {
            compiled.values.forEach { it.reset() }
            throw t
        }

        val meshes = Collections.newSetFromMap(IdentityHashMap<SectionMesh, Boolean>())
        compiled.values.forEach { section ->
            val mesh = section.getSectionMesh()
            if (mesh is CompiledSectionMesh) meshes.add(mesh)
        }

        return Session(overrideDispatcher, regularSections, compiled, meshes).also { active = it }
    }

    /** Ends a vanilla override session: stops drawing override meshes and frees their GPU allocations. */
    private fun endVanilla(session: Session) {
        if (active === session) active = null
        session.overrideSections.values.forEach { it.reset() }
    }

    /** Level renderer hook: adds the override sections that the occlusion graph did not select to the draw list. */
    @JvmStatic
    fun visibleSections(visible: ObjectArrayList<SectionRenderDispatcher.RenderSection>): ObjectArrayList<SectionRenderDispatcher.RenderSection> {
        val session = active ?: return visible
        val present = Collections.newSetFromMap(IdentityHashMap<SectionRenderDispatcher.RenderSection, Boolean>(visible.size))
        present.addAll(visible)

        val result = ObjectArrayList(visible)
        for (section in session.regularSections) {
            if (present.add(section)) result.add(section)
        }
        return result
    }

    /** Level renderer hook: the mesh drawn for a section, which is the override mesh for overridden sections. */
    @JvmStatic
    fun sectionMesh(section: SectionRenderDispatcher.RenderSection): SectionMesh {
        val override = active?.overrideSections?.get(section.sectionNode)
        return override?.getSectionMesh() ?: section.getSectionMesh()
    }

    /** Level renderer hook: the GPU slice for a mesh, looked up in the override dispatcher for override meshes. */
    @JvmStatic
    fun renderSectionSlice(
        regular: SectionRenderDispatcher,
        mesh: SectionMesh,
        layer: ChunkSectionLayer
    ): SectionRenderDispatcher.RenderSectionBufferSlice? {
        val session = active
        if (session != null && session.overrideMeshes.contains(mesh)) {
            return session.dispatcher.getRenderSectionSlice(mesh, layer)
        }
        return regular.getRenderSectionSlice(mesh, layer)
    }

    private fun createRegion(
        level: ClientLevel,
        node: Long,
        overrides: Long2ObjectMap<BlockState>,
        copies: Long2ObjectOpenHashMap<SectionCopy>
    ): RenderSectionRegion {
        val sectionX = SectionPos.x(node)
        val sectionY = SectionPos.y(node)
        val sectionZ = SectionPos.z(node)
        val minX = sectionX - 1
        val minY = sectionY - 1
        val minZ = sectionZ - 1

        val sections = arrayOfNulls<SectionCopy>(27)
        for (x in minX..sectionX + 1) for (y in minY..sectionY + 1) for (z in minZ..sectionZ + 1) {
            sections[RenderSectionRegion.index(minX, minY, minZ, x, y, z)] = copyOf(level, x, y, z, copies)
        }
        return OverrideRegion(level, minX, minY, minZ, sections.requireNoNulls(), overrides)
    }

    private fun copyOf(level: ClientLevel, x: Int, y: Int, z: Int, copies: Long2ObjectOpenHashMap<SectionCopy>): SectionCopy {
        val key = SectionPos.asLong(x, y, z)
        copies.get(key)?.let { return it }
        val chunk = level.getChunk(x, z)
        return SectionCopy(chunk, chunk.getSectionIndexFromSectionY(y)).also { copies.put(key, it) }
    }

    private const val MAX_UPLOAD_ATTEMPTS = 64

    /** One override pass: the regular sections it replaces and their compiled override sections. */
    class Session internal constructor(
        internal val dispatcher: SectionRenderDispatcher,
        internal val regularSections: List<SectionRenderDispatcher.RenderSection>,
        internal val overrideSections: Long2ObjectMap<SectionRenderDispatcher.RenderSection>,
        internal val overrideMeshes: Set<SectionMesh>,
    ) : AutoCloseable {
        override fun close() = endVanilla(this)
    }
}

/**
 * A section region whose block, fluid and block-entity queries see the overrides first. Each overridden position
 * is answered by its override state; everything else reads the real level snapshot, so faces and ambient occlusion
 * of neighbouring sections account for the override.
 */
private class OverrideRegion(
    level: ClientLevel,
    minSectionX: Int,
    minSectionY: Int,
    minSectionZ: Int,
    sections: Array<SectionCopy>,
    private val overrides: Long2ObjectMap<BlockState>,
) : RenderSectionRegion(level, minSectionX, minSectionY, minSectionZ, sections) {

    override fun getBlockState(pos: BlockPos): BlockState =
        overrides.get(pos.asLong()) ?: super.getBlockState(pos)

    override fun getFluidState(pos: BlockPos): FluidState =
        overrides.get(pos.asLong())?.fluidState ?: super.getFluidState(pos)

    override fun getBlockEntity(pos: BlockPos): BlockEntity? =
        if (overrides.containsKey(pos.asLong())) null else super.getBlockEntity(pos)
}
