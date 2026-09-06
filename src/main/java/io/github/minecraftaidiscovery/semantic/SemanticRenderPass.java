package io.github.minecraftaidiscovery.semantic;

public final class SemanticRenderPass {
    private static final ThreadLocal<SemanticRenderLayer> ACTIVE_LAYER = new ThreadLocal<>();

    private SemanticRenderPass() {
    }

    public static void run(SemanticRenderLayer layer, Runnable renderer) {
        if (layer == null) {
            throw new IllegalArgumentException("Semantic render layer is missing");
        }
        if (ACTIVE_LAYER.get() != null) {
            throw new IllegalStateException("Semantic render pass cannot be nested");
        }
        ACTIVE_LAYER.set(layer);
        try {
            renderer.run();
        } finally {
            ACTIVE_LAYER.remove();
        }
    }

    public static boolean isActive() {
        return ACTIVE_LAYER.get() != null;
    }

    public static SemanticRenderLayer requireLayer() {
        SemanticRenderLayer layer = ACTIVE_LAYER.get();
        if (layer == null) {
            throw new IllegalStateException("No semantic render layer is active");
        }
        return layer;
    }
}
