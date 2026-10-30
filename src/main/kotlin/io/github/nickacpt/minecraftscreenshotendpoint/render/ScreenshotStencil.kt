package io.github.nickacpt.minecraftscreenshotendpoint.render

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.commands.RenderPassDescriptor
import com.mojang.renderpearl.api.textures.GpuTextureView
import io.github.nickacpt.minecraftscreenshotendpoint.controllers.ScreenshotRequestException
import io.ktor.http.HttpStatusCode
import com.mojang.blaze3d.platform.NativeImage
import java.util.Optional
import org.joml.Vector4fc

/**
 * A rectangle in screenshot pixels, origin top-left, y down. Always non-empty and inside the image once parsed.
 */
data class StencilRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
}

/**
 * Parses the repeatable `stencil=<x>,<y>,<w>,<h>` query values.
 *
 * Malformed values (not four integers, or `w`/`h` not positive) fail with 400. Rects are clamped to the image;
 * a rect that lies entirely outside the image is dropped. If nothing remains, the request fails with 400, because
 * a fully transparent image is never what a stencil request means.
 */
fun parseStencils(values: List<String>, imageWidth: Int, imageHeight: Int): List<StencilRect> {
    if (values.isEmpty()) return emptyList()

    val rects = values.mapNotNull { value ->
        val parts = value.split(",")
        if (parts.size != 4) {
            throw ScreenshotRequestException(HttpStatusCode.BadRequest, "stencil must be x,y,w,h (got '$value')")
        }
        val ints = parts.map { it.trim().toIntOrNull() }
        if (ints.any { it == null }) {
            throw ScreenshotRequestException(HttpStatusCode.BadRequest, "stencil must contain integers (got '$value')")
        }
        val (x, y, w, h) = ints.map { it!! }
        if (w <= 0 || h <= 0) {
            throw ScreenshotRequestException(HttpStatusCode.BadRequest, "stencil width and height must be positive (got '$value')")
        }

        val left = x.toLong().coerceIn(0L, imageWidth.toLong()).toInt()
        val top = y.toLong().coerceIn(0L, imageHeight.toLong()).toInt()
        val right = (x.toLong() + w).coerceIn(0L, imageWidth.toLong()).toInt()
        val bottom = (y.toLong() + h).coerceIn(0L, imageHeight.toLong()).toInt()
        if (right <= left || bottom <= top) null else StencilRect(left, top, right - left, bottom - top)
    }

    if (rects.isEmpty()) {
        throw ScreenshotRequestException(HttpStatusCode.BadRequest, "stencil does not overlap the ${imageWidth}x$imageHeight image")
    }
    return rects
}

/**
 * Restricts rendering of the screenshot pass to the stencil.
 *
 * GPU work is limited with a scissor: [begin] sets the bounding box of all rects as the render area of every render
 * pass that targets the screenshot's main colour target. A single scissor cannot express a union of disjoint rects, so
 * pixels inside the bounding box but outside every rect are still drawn; they are made transparent by [applyAlpha]
 * when the image is read back. Only the main target is clipped; intermediate targets stay full size, because later
 * passes may sample them at neighbouring pixels.
 *
 * Only one screenshot renders at a time, so the state is a plain global, like [ScreenshotBlockOverrides].
 */
object ScreenshotStencil {
    private class Active(val target: GpuTextureView, val bounds: RenderPass.RenderArea)

    @Volatile
    private var active: Active? = null

    /**
     * Activates the stencil for the main [target] of the screenshot. [imageHeight] is needed to convert the top-left
     * stencil origin into the bottom-left origin used by render-pass scissors.
     */
    @JvmStatic
    fun begin(target: RenderTarget, rects: List<StencilRect>, imageHeight: Int) {
        if (rects.isEmpty()) return
        val view = target.getColorTextureView() ?: throw IllegalStateException("Main render target has no colour view")
        val left = rects.minOf { it.x }
        val top = rects.minOf { it.y }
        val right = rects.maxOf { it.right }
        val bottom = rects.maxOf { it.bottom }
        val bounds = RenderPass.RenderArea(left, imageHeight - bottom, right - left, bottom - top)
        active = Active(view, bounds)
    }

    @JvmStatic
    fun end() {
        active = null
    }

    /**
     * Returns [descriptor] with its render area limited to the stencil bounds when it draws into the main target,
     * otherwise returns [descriptor] unchanged. Called for every render pass created through the frontend encoder.
     */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun restrict(descriptor: RenderPassDescriptor): RenderPassDescriptor {
        val state = active ?: return descriptor
        val targetsMain = descriptor.colorAttachments().any { it != null && it.textureView() === state.target }
        if (!targetsMain) return descriptor

        val area = descriptor.renderArea()
        val b = state.bounds
        val left = maxOf(area.x(), b.x())
        val bottom = maxOf(area.y(), b.y())
        val right = minOf(area.x() + area.width(), b.x() + b.width())
        val top = minOf(area.y() + area.height(), b.y() + b.height())
        if (right <= left || top <= bottom) return descriptor

        return RenderPassDescriptor(
            descriptor.label(),
            // Null slots are unused attachment indices and must keep their position; the record's type just omits the nullability.
            descriptor.colorAttachments() as List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>>,
            descriptor.depthAttachment(),
            RenderPass.RenderArea(left, bottom, right - left, top - bottom),
        )
    }

    /**
     * Makes pixels inside the stencil opaque and pixels outside it fully transparent. The image is top-down, the same
     * as the screenshot's pixel coordinates.
     */
    fun applyAlpha(image: NativeImage, rects: List<StencilRect>) {
        val width = image.width
        val height = image.height
        val inside = BooleanArray(width * height)
        for (rect in rects) {
            for (y in rect.y until minOf(rect.bottom, height)) {
                val row = y * width
                for (x in rect.x until minOf(rect.right, width)) {
                    inside[row + x] = true
                }
            }
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = image.getPixel(x, y)
                image.setPixel(x, y, if (inside[y * width + x]) pixel or 0xFF000000.toInt() else pixel and 0x00FFFFFF)
            }
        }
    }
}
