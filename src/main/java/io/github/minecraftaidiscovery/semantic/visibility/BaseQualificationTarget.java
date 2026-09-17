package io.github.minecraftaidiscovery.semantic.visibility;

final class BaseQualificationTarget {
    enum Control {
        NONE,
        POSITIVE_CATCH,
        NEGATIVE_BLINDING
    }

    private final String targetId;
    private final BaseQualificationStudyManifest.Capture capture;
    private final BaseQualificationStudyManifest.Pair pair;
    private final BaseQualificationTargetRegion region;
    private final Control control;
    private final BaseQualificationTargetMask mask;

    BaseQualificationTarget(
        String targetId,
        BaseQualificationStudyManifest.Capture capture,
        BaseQualificationStudyManifest.Pair pair,
        BaseQualificationTargetRegion region,
        Control control,
        BaseQualificationTargetMask mask
    ) {
        if (targetId == null || targetId.isEmpty() || capture == null || pair == null
            || region == null || control == null || mask == null) {
            throw new IllegalArgumentException("Calibration target is invalid");
        }
        this.targetId = targetId;
        this.capture = capture;
        this.pair = pair;
        this.region = region;
        this.control = control;
        this.mask = mask;
    }

    String getTargetId() {
        return targetId;
    }

    BaseQualificationStudyManifest.Capture getCapture() {
        return capture;
    }

    BaseQualificationStudyManifest.Pair getPair() {
        return pair;
    }

    BaseQualificationTargetRegion getRegion() {
        return region;
    }

    Control getControl() {
        return control;
    }

    BaseQualificationTargetMask getMask() {
        return mask;
    }
}
