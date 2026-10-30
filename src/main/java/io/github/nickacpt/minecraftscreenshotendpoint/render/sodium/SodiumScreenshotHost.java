package io.github.nickacpt.minecraftscreenshotendpoint.render.sodium;

/**
 * Implemented by Sodium's {@code SodiumWorldRenderer} (see {@code SodiumWorldRendererMixin}). Swaps the renderer's
 * terrain managers for private ones for the duration of one screenshot pass.
 */
public interface SodiumScreenshotHost {
    void mse$beginOverridePass();

    void mse$endOverridePass();
}
