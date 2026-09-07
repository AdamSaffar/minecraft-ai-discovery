package io.github.minecraftaidiscovery.semantic.translucent;

import io.github.minecraftaidiscovery.semantic.SemanticClassEncoding;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifestRuntime;

public final class TranslucentProbeVertexEncoding {
    private static final Contribution NONE = new Contribution(
        BlockClassManifest.BACKGROUND_CLASS_ID,
        TranslucentProbeSource.NONE
    );
    private static final ThreadLocal<Contribution> ACTIVE = ThreadLocal.withInitial(() -> NONE);

    private TranslucentProbeVertexEncoding() {
    }

    public static Contribution beginBlock(String registryIdentifier) {
        return begin(
            BlockClassManifestRuntime.require().requireClassId(registryIdentifier),
            TranslucentProbeSource.BLOCK_MODEL
        );
    }

    public static Contribution beginFluid(String registryIdentifier) {
        return begin(
            BlockClassManifestRuntime.require().requireClassId(registryIdentifier),
            TranslucentProbeSource.FLUID
        );
    }

    public static Contribution beginBackground() {
        return begin(BlockClassManifest.BACKGROUND_CLASS_ID, TranslucentProbeSource.NONE);
    }

    public static Contribution begin(int classId, TranslucentProbeSource source) {
        Contribution previous = ACTIVE.get();
        ACTIVE.set(new Contribution(classId, source));
        return previous;
    }

    public static void restore(Contribution contribution) {
        if (contribution == null || contribution == NONE) {
            ACTIVE.remove();
        } else {
            ACTIVE.set(contribution);
        }
    }

    public static Contribution getActive() {
        return ACTIVE.get();
    }

    public static final class Contribution {
        private final int classId;
        private final TranslucentProbeSource source;

        Contribution(int classId, TranslucentProbeSource source) {
            this.classId = SemanticClassEncoding.requireUnsigned16(classId);
            if (source == null) {
                throw new IllegalArgumentException("Translucent probe source is missing");
            }
            if ((classId == BlockClassManifest.BACKGROUND_CLASS_ID)
                != (source == TranslucentProbeSource.NONE)) {
                throw new IllegalArgumentException(
                    "Background class and source-kind sentinel must be used together"
                );
            }
            this.source = source;
        }

        public int getClassId() {
            return classId;
        }

        public TranslucentProbeSource getSource() {
            return source;
        }
    }
}
