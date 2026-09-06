package io.github.minecraftaidiscovery.semantic;

import net.minecraft.client.render.RenderLayer;

public final class SemanticMinecraftRenderLayers {
    private SemanticMinecraftRenderLayers() {
    }

    public static SemanticRenderLayer classify(RenderLayer layer) {
        return classifyIdentity(
            layer,
            RenderLayer.getSolid(),
            RenderLayer.getCutoutMipped(),
            RenderLayer.getCutout()
        );
    }

    static SemanticRenderLayer classifyIdentity(
        Object layer,
        Object solid,
        Object cutoutMipped,
        Object cutout
    ) {
        if (layer == solid) {
            return SemanticRenderLayer.SOLID;
        }
        if (layer == cutoutMipped) {
            return SemanticRenderLayer.CUTOUT_MIPPED;
        }
        if (layer == cutout) {
            return SemanticRenderLayer.CUTOUT;
        }
        return null;
    }

    public static RenderLayer requireMinecraftLayer(SemanticRenderLayer layer) {
        if (layer == null) {
            throw new IllegalArgumentException("Semantic render layer is missing");
        }
        switch (layer) {
            case SOLID:
                return RenderLayer.getSolid();
            case CUTOUT_MIPPED:
                return RenderLayer.getCutoutMipped();
            case CUTOUT:
                return RenderLayer.getCutout();
            default:
                throw new IllegalArgumentException("Unsupported semantic render layer: " + layer);
        }
    }
}
