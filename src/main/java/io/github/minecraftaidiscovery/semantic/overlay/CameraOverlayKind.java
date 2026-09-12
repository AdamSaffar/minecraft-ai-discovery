package io.github.minecraftaidiscovery.semantic.overlay;

public enum CameraOverlayKind {
    INSIDE_BLOCK("inside_block", 1, false),
    UNDERWATER("underwater", 1, true),
    FIRE("fire", 2, true);

    private final String metadataName;
    private final int expectedDrawCount;
    private final boolean sourceAlphaRetention;

    CameraOverlayKind(
        String metadataName,
        int expectedDrawCount,
        boolean sourceAlphaRetention
    ) {
        this.metadataName = metadataName;
        this.expectedDrawCount = expectedDrawCount;
        this.sourceAlphaRetention = sourceAlphaRetention;
    }

    public String getMetadataName() {
        return metadataName;
    }

    public int getExpectedDrawCount() {
        return expectedDrawCount;
    }

    public boolean usesSourceAlphaRetention() {
        return sourceAlphaRetention;
    }
}
