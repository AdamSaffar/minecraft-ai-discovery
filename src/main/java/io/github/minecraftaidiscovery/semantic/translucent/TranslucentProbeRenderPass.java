package io.github.minecraftaidiscovery.semantic.translucent;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.RenderLayer;

public final class TranslucentProbeRenderPass {
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();

    private TranslucentProbeRenderPass() {
    }

    public static void run(
        RenderLayer layer,
        TranslucentProbePassMode mode,
        Framebuffer target,
        int previousDepthTexture,
        Runnable renderer
    ) {
        if (!TranslucentProbeConfiguration.isEnabled()) {
            throw new IllegalStateException("Translucent probe is not enabled");
        }
        if (layer == null || mode == null || target == null || renderer == null) {
            throw new IllegalArgumentException("Translucent probe render pass is incomplete");
        }
        if (mode.usesPreviousDepth() != (previousDepthTexture >= 0)) {
            throw new IllegalArgumentException("Previous depth does not match the probe pass mode");
        }
        boolean layerMatches = mode == TranslucentProbePassMode.SOLID_FLUID
            ? layer == RenderLayer.getSolid()
            : layer == RenderLayer.getTranslucent();
        if (!layerMatches) {
            throw new IllegalArgumentException("Probe pass mode does not match the Minecraft render layer");
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("Translucent probe render pass cannot be nested");
        }

        ACTIVE.set(new Context(layer, mode, target, previousDepthTexture));
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
            throw new IllegalStateException("No translucent probe render pass is active");
        }
        return context;
    }

    public static final class Context {
        private final RenderLayer layer;
        private final TranslucentProbePassMode mode;
        private final Framebuffer target;
        private final int previousDepthTexture;

        private Context(
            RenderLayer layer,
            TranslucentProbePassMode mode,
            Framebuffer target,
            int previousDepthTexture
        ) {
            this.layer = layer;
            this.mode = mode;
            this.target = target;
            this.previousDepthTexture = previousDepthTexture;
        }

        public RenderLayer getLayer() {
            return layer;
        }

        public TranslucentProbePassMode getMode() {
            return mode;
        }

        public Framebuffer getTarget() {
            return target;
        }

        public int getPreviousDepthTexture() {
            return previousDepthTexture;
        }
    }
}
