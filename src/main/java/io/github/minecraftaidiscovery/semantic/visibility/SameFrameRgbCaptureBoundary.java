package io.github.minecraftaidiscovery.semantic.visibility;

public enum SameFrameRgbCaptureBoundary {
    RENDER_HAND_RETURN("render_hand_return", true),
    RENDER_WORLD_RETURN_WITHOUT_HAND("render_world_return_without_hand", false);

    private final String metadataName;
    private final boolean handAndCameraOverlayStageComplete;

    SameFrameRgbCaptureBoundary(
        String metadataName,
        boolean handAndCameraOverlayStageComplete
    ) {
        this.metadataName = metadataName;
        this.handAndCameraOverlayStageComplete = handAndCameraOverlayStageComplete;
    }

    public String getMetadataName() {
        return metadataName;
    }

    public boolean isHandAndCameraOverlayStageComplete() {
        return handAndCameraOverlayStageComplete;
    }
}
