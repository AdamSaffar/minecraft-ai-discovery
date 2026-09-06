package io.github.minecraftaidiscovery.semantic.manifest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BlockClassVocabulary {
    private final BlockClassManifest manifest;
    private final Map<String, Integer> classIdsByRegistryIdentifier;
    private final Map<Integer, String> registryIdentifiersByClassId;

    public BlockClassVocabulary(BlockClassManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("Block class manifest is missing");
        }

        Map<String, Integer> byRegistryIdentifier = new LinkedHashMap<>();
        Map<Integer, String> byClassId = new LinkedHashMap<>();
        for (BlockClassManifest.Entry entry : manifest.getEntries()) {
            String identifier = entry.getRegistryIdentifier();
            int classId = entry.getSemanticClassId();
            if (byRegistryIdentifier.put(identifier, classId) != null) {
                throw new IllegalArgumentException("Duplicate block registry identifier: " + identifier);
            }
            if (byClassId.put(classId, identifier) != null) {
                throw new IllegalArgumentException("Duplicate semantic class ID: " + classId);
            }
        }

        this.manifest = manifest;
        this.classIdsByRegistryIdentifier = Collections.unmodifiableMap(byRegistryIdentifier);
        this.registryIdentifiersByClassId = Collections.unmodifiableMap(byClassId);
    }

    public int requireClassId(String registryIdentifier) {
        Integer classId = classIdsByRegistryIdentifier.get(registryIdentifier);
        if (classId == null) {
            throw new IllegalStateException(
                "Block registry identifier is absent from the active semantic manifest: "
                    + registryIdentifier
            );
        }
        return classId;
    }

    public String requireRegistryIdentifier(int classId) {
        String registryIdentifier = registryIdentifiersByClassId.get(classId);
        if (registryIdentifier == null) {
            throw new IllegalStateException(
                "Semantic class ID is absent from the active block manifest: " + classId
            );
        }
        return registryIdentifier;
    }

    public BlockClassManifest getManifest() {
        return manifest;
    }

    public int getEntryCount() {
        return classIdsByRegistryIdentifier.size();
    }

    public String getFingerprintSha256() {
        return manifest.getFingerprintSha256();
    }
}
