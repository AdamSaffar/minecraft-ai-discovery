package io.github.minecraftaidiscovery.semantic.manifest;

import java.util.concurrent.atomic.AtomicReference;

public final class BlockClassManifestRuntime {
    private static final AtomicReference<BlockClassVocabulary> ACTIVE = new AtomicReference<>();

    private BlockClassManifestRuntime() {
    }

    public static BlockClassVocabulary initialize(BlockClassManifest manifest) {
        BlockClassVocabulary candidate = new BlockClassVocabulary(manifest);
        BlockClassVocabulary current = ACTIVE.get();
        if (current == null && ACTIVE.compareAndSet(null, candidate)) {
            return candidate;
        }

        current = ACTIVE.get();
        if (!current.getFingerprintSha256().equals(candidate.getFingerprintSha256())) {
            throw new IllegalStateException(
                "The active block class manifest cannot be replaced with a different fingerprint"
            );
        }
        return current;
    }

    public static BlockClassVocabulary require() {
        BlockClassVocabulary vocabulary = ACTIVE.get();
        if (vocabulary == null) {
            throw new IllegalStateException("The block class manifest has not been initialized");
        }
        return vocabulary;
    }
}
