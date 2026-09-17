package io.github.minecraftaidiscovery.semantic.visibility;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BaseQualificationCapture {
    private final BaseInfluenceProbeCapture influenceCapture;
    private final int[] baseClassIds;
    private final int[] baseDepthBits;
    private final byte[] exactZeroInfluence;
    private final Map<String, String> artifactSha256;

    BaseQualificationCapture(
        BaseInfluenceProbeCapture influenceCapture,
        int[] baseClassIds,
        int[] baseDepthBits,
        byte[] exactZeroInfluence,
        Map<String, String> artifactSha256
    ) {
        if (influenceCapture == null) {
            throw new IllegalArgumentException("Base-qualification capture is missing");
        }
        int pixels = influenceCapture.getComposition().getWidth()
            * influenceCapture.getComposition().getHeight();
        if (baseClassIds == null || baseClassIds.length != pixels
            || baseDepthBits == null || baseDepthBits.length != pixels
            || exactZeroInfluence == null || exactZeroInfluence.length != pixels) {
            throw new IllegalArgumentException("Base-qualification buffer length is invalid");
        }
        if (artifactSha256 == null || artifactSha256.isEmpty()) {
            throw new IllegalArgumentException("Base-qualification artifact hashes are missing");
        }
        for (byte value : exactZeroInfluence) {
            int unsigned = Byte.toUnsignedInt(value);
            if (unsigned != 0 && unsigned != 1) {
                throw new IllegalArgumentException("Exact-zero mask must contain only 0 or 1");
            }
        }
        this.influenceCapture = influenceCapture;
        this.baseClassIds = baseClassIds.clone();
        this.baseDepthBits = baseDepthBits.clone();
        this.exactZeroInfluence = exactZeroInfluence.clone();
        this.artifactSha256 = Collections.unmodifiableMap(
            new LinkedHashMap<>(artifactSha256)
        );
    }

    public String getCaptureName() {
        return influenceCapture.getCaptureName();
    }

    public int getWidth() {
        return influenceCapture.getComposition().getWidth();
    }

    public int getHeight() {
        return influenceCapture.getComposition().getHeight();
    }

    public BaseInfluenceCompositionResult getComposition() {
        return influenceCapture.getComposition();
    }

    public SameFrameRgbResult getSameFrameRgb() {
        return influenceCapture.getSameFrameRgb();
    }

    public int[] copyBaseClassIds() {
        return baseClassIds.clone();
    }

    public int[] copyBaseDepthBits() {
        return baseDepthBits.clone();
    }

    public byte[] copyExactZeroInfluence() {
        return exactZeroInfluence.clone();
    }

    public Map<String, String> getArtifactSha256() {
        return artifactSha256;
    }
}
