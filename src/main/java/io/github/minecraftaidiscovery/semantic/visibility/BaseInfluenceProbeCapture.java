package io.github.minecraftaidiscovery.semantic.visibility;

public final class BaseInfluenceProbeCapture {
    private final String captureName;
    private final BaseInfluenceCompositionResult composition;

    BaseInfluenceProbeCapture(
        String captureName,
        BaseInfluenceCompositionResult composition
    ) {
        if (captureName == null || captureName.isEmpty()) {
            throw new IllegalArgumentException("Probe capture name is missing");
        }
        if (composition == null) {
            throw new IllegalArgumentException("Probe composition result is missing");
        }
        this.captureName = captureName;
        this.composition = composition;
    }

    public String getCaptureName() {
        return captureName;
    }

    public BaseInfluenceCompositionResult getComposition() {
        return composition;
    }
}
