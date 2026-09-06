package io.github.minecraftaidiscovery.semantic;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SemanticClassRegistry {
    public static final int BACKGROUND_CLASS_ID = 0;
    public static final int UNCLASSIFIED_MARKER_ALPHA = 255;

    private static final List<SemanticClass> CLASSES = Collections.unmodifiableList(Arrays.asList(
        new SemanticClass(1, "minecraft:stone", 253, 0xE63946),
        new SemanticClass(2, "minecraft:oak_log", 254, 0x2A9D8F)
    ));
    private static final Map<String, SemanticClass> BY_REGISTRY_ID;
    private static final Map<Integer, SemanticClass> BY_MARKER_ALPHA;

    static {
        Map<String, SemanticClass> byRegistryId = new LinkedHashMap<>();
        Map<Integer, SemanticClass> byMarkerAlpha = new LinkedHashMap<>();
        for (SemanticClass semanticClass : CLASSES) {
            byRegistryId.put(semanticClass.getRegistryId(), semanticClass);
            byMarkerAlpha.put(semanticClass.getMarkerAlpha(), semanticClass);
        }
        BY_REGISTRY_ID = Collections.unmodifiableMap(byRegistryId);
        BY_MARKER_ALPHA = Collections.unmodifiableMap(byMarkerAlpha);
    }

    private SemanticClassRegistry() {
    }

    public static List<SemanticClass> getClasses() {
        return CLASSES;
    }

    public static int classIdForRegistryId(String registryId) {
        SemanticClass semanticClass = BY_REGISTRY_ID.get(registryId);
        return semanticClass == null ? BACKGROUND_CLASS_ID : semanticClass.getClassId();
    }

    public static int markerAlphaForRegistryId(String registryId) {
        SemanticClass semanticClass = BY_REGISTRY_ID.get(registryId);
        return semanticClass == null ? UNCLASSIFIED_MARKER_ALPHA : semanticClass.getMarkerAlpha();
    }

    public static int classIdForMarkerAlpha(int markerAlpha) {
        SemanticClass semanticClass = BY_MARKER_ALPHA.get(markerAlpha);
        return semanticClass == null ? BACKGROUND_CLASS_ID : semanticClass.getClassId();
    }

    public static int falseColorRgbForClassId(int classId) {
        for (SemanticClass semanticClass : CLASSES) {
            if (semanticClass.getClassId() == classId) {
                return semanticClass.getFalseColorRgb();
            }
        }
        return 0;
    }
}
