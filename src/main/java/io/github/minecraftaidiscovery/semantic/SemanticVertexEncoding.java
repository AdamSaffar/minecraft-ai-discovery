package io.github.minecraftaidiscovery.semantic;

public final class SemanticVertexEncoding {
    public static final int INACTIVE = -1;

    private static final ThreadLocal<Integer> ACTIVE_MARKER_ALPHA =
        ThreadLocal.withInitial(() -> INACTIVE);

    private SemanticVertexEncoding() {
    }

    public static int beginBlock(String registryId) {
        int previous = ACTIVE_MARKER_ALPHA.get();
        ACTIVE_MARKER_ALPHA.set(SemanticClassRegistry.markerAlphaForRegistryId(registryId));
        return previous;
    }

    public static void restore(int markerAlpha) {
        if (markerAlpha == INACTIVE) {
            ACTIVE_MARKER_ALPHA.remove();
        } else {
            ACTIVE_MARKER_ALPHA.set(markerAlpha);
        }
    }

    public static int getActiveMarkerAlpha() {
        return ACTIVE_MARKER_ALPHA.get();
    }
}
