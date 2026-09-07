package io.github.minecraftaidiscovery.semantic.translucent;

import net.minecraft.client.render.RenderLayer;

public final class TranslucentProbeMinecraftRenderLayers {
    private TranslucentProbeMinecraftRenderLayers() {
    }

    public static TranslucentProbeTerrainLayer classify(RenderLayer layer) {
        return classifyIdentity(
            layer,
            RenderLayer.getSolid(),
            RenderLayer.getCutoutMipped(),
            RenderLayer.getCutout(),
            RenderLayer.getTranslucent()
        );
    }

    static TranslucentProbeTerrainLayer classifyIdentity(
        Object layer,
        Object solid,
        Object cutoutMipped,
        Object cutout,
        Object translucent
    ) {
        if (layer == solid) {
            return TranslucentProbeTerrainLayer.SOLID;
        }
        if (layer == cutoutMipped) {
            return TranslucentProbeTerrainLayer.CUTOUT_MIPPED;
        }
        if (layer == cutout) {
            return TranslucentProbeTerrainLayer.CUTOUT;
        }
        if (layer == translucent) {
            return TranslucentProbeTerrainLayer.TRANSLUCENT;
        }
        return null;
    }
}
