package io.github.minecraftaidiscovery.semantic.visibility;

public final class BaseInfluenceProbeCapture {
    private final String captureName;
    private final BaseInfluenceCompositionResult composition;
    private final SameFrameRgbResult sameFrameRgb;

    BaseInfluenceProbeCapture(
        String captureName,
        BaseInfluenceCompositionResult composition,
        SameFrameRgbResult sameFrameRgb
    ) {
        if (captureName == null || captureName.isEmpty()) {
            throw new IllegalArgumentException("Probe capture name is missing");
        }
        if (composition == null) {
            throw new IllegalArgumentException("Probe composition result is missing");
        }
        if (sameFrameRgb == null
            || sameFrameRgb.getWidth() != composition.getWidth()
            || sameFrameRgb.getHeight() != composition.getHeight()) {
            throw new IllegalArgumentException(
                "Same-frame RGB result does not match the probe composition"
            );
        }
        this.captureName = captureName;
        this.composition = composition;
        this.sameFrameRgb = sameFrameRgb;
    }

    public String getCaptureName() {
        return captureName;
    }

    public BaseInfluenceCompositionResult getComposition() {
        return composition;
    }

    public SameFrameRgbResult getSameFrameRgb() {
        return sameFrameRgb;
    }
}
