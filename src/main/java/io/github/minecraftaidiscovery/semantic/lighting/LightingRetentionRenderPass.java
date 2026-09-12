package io.github.minecraftaidiscovery.semantic.lighting;

import io.github.minecraftaidiscovery.semantic.SemanticRenderLayer;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeConfiguration;
import net.minecraft.client.gl.Framebuffer;

public final class LightingRetentionRenderPass {
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();

    private LightingRetentionRenderPass() {
    }

    public static void run(
        SemanticRenderLayer layer,
        Framebuffer target,
        Runnable renderer
    ) {
        if (!TranslucentProbeConfiguration.isEnabled()) {
            throw new IllegalStateException("Translucent researcher probe is not enabled");
        }
        if (layer == null || target == null || renderer == null) {
            throw new IllegalArgumentException("Lighting-retention render pass is incomplete");
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("Lighting-retention render pass cannot be nested");
        }

        ACTIVE.set(new Context(layer, target));
        try {
            renderer.run();
        } finally {
            ACTIVE.remove();
        }
    }

    public static boolean isActive() {
        return ACTIVE.get() != null;
    }

    public static Context requireContext() {
        Context context = ACTIVE.get();
        if (context == null) {
            throw new IllegalStateException("No lighting-retention render pass is active");
        }
        return context;
    }

    public static final class Context {
        private final SemanticRenderLayer layer;
        private final Framebuffer target;

        private Context(SemanticRenderLayer layer, Framebuffer target) {
            this.layer = layer;
            this.target = target;
        }

        public SemanticRenderLayer getLayer() {
            return layer;
        }

        public Framebuffer getTarget() {
            return target;
        }
    }
}
