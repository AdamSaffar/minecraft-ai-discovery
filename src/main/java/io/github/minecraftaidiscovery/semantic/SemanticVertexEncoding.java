package io.github.minecraftaidiscovery.semantic;

import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifestRuntime;

public final class SemanticVertexEncoding {
    public static final int INACTIVE = -1;

    private static final ThreadLocal<Integer> ACTIVE_CLASS_ID =
        ThreadLocal.withInitial(() -> INACTIVE);

    private SemanticVertexEncoding() {
    }

    public static int beginBlock(String registryId) {
        return beginClassId(BlockClassManifestRuntime.require().requireClassId(registryId));
    }

    public static int beginBackground() {
        return beginClassId(BlockClassManifest.BACKGROUND_CLASS_ID);
    }

    public static int beginClassId(int classId) {
        int previous = ACTIVE_CLASS_ID.get();
        ACTIVE_CLASS_ID.set(SemanticClassEncoding.requireUnsigned16(classId));
        return previous;
    }

    public static void restore(int classId) {
        if (classId == INACTIVE) {
            ACTIVE_CLASS_ID.remove();
        } else {
            ACTIVE_CLASS_ID.set(classId);
        }
    }

    public static int getActiveClassId() {
        int classId = ACTIVE_CLASS_ID.get();
        return classId == INACTIVE ? BlockClassManifest.BACKGROUND_CLASS_ID : classId;
    }

    static int getRawActiveClassId() {
        return ACTIVE_CLASS_ID.get();
    }
}
