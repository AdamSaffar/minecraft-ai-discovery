package io.github.minecraftaidiscovery.semantic;

public final class SemanticRenderPass {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private SemanticRenderPass() {
    }

    public static void run(Runnable renderer) {
        if (ACTIVE.get()) {
            throw new IllegalStateException("Semantic render pass cannot be nested");
        }
        ACTIVE.set(true);
        try {
            renderer.run();
        } finally {
            ACTIVE.remove();
        }
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }
}
