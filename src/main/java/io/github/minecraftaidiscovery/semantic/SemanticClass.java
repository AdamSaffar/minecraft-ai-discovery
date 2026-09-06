package io.github.minecraftaidiscovery.semantic;

public final class SemanticClass {
    private final int classId;
    private final String registryId;
    private final int markerAlpha;
    private final int falseColorRgb;

    SemanticClass(int classId, String registryId, int markerAlpha, int falseColorRgb) {
        this.classId = classId;
        this.registryId = registryId;
        this.markerAlpha = markerAlpha;
        this.falseColorRgb = falseColorRgb;
    }

    public int getClassId() {
        return classId;
    }

    public String getRegistryId() {
        return registryId;
    }

    public int getMarkerAlpha() {
        return markerAlpha;
    }

    public int getFalseColorRgb() {
        return falseColorRgb;
    }
}
