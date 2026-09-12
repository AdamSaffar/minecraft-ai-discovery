package io.github.minecraftaidiscovery.semantic.translucent;

import io.github.minecraftaidiscovery.semantic.SemanticRenderLayer;
import net.minecraft.client.gl.Framebuffer;

public final class FogRetentionRenderPass {
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();

    private FogRetentionRenderPass() {
    }

    public static void run(
        SemanticRenderLayer layer,
        Framebuffer target,
        FogRetentionParameters parameters,
        Runnable renderer
    ) {
        if (!TranslucentProbeConfiguration.isEnabled()) {
            throw new IllegalStateException("Translucent probe is not enabled");
        }
        if (layer == null || target == null || parameters == null || renderer == null) {
            throw new IllegalArgumentException("Fog retention render pass is incomplete");
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("Fog retention render pass cannot be nested");
        }

        ACTIVE.set(new Context(layer, target, parameters));
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
            throw new IllegalStateException("No fog retention render pass is active");
        }
        return context;
    }

    public static final class Context {
        private final SemanticRenderLayer layer;
        private final Framebuffer target;
        private final FogRetentionParameters parameters;

        private Context(
            SemanticRenderLayer layer,
            Framebuffer target,
            FogRetentionParameters parameters
        ) {
            this.layer = layer;
            this.target = target;
            this.parameters = parameters;
        }

        public SemanticRenderLayer getLayer() {
            return layer;
        }

        public Framebuffer getTarget() {
            return target;
        }

        public FogRetentionParameters getParameters() {
            return parameters;
        }
    }
}
