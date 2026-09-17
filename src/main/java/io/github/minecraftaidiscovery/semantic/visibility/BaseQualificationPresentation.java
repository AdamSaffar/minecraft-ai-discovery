package io.github.minecraftaidiscovery.semantic.visibility;

final class BaseQualificationPresentation {
    enum Response {
        LEFT,
        RIGHT,
        UNCERTAIN
    }

    private final String trialId;
    private final int batch;
    private final BaseQualificationTarget target;
    private final boolean identityAOnLeft;
    private final boolean mirrored;

    BaseQualificationPresentation(
        String trialId,
        int batch,
        BaseQualificationTarget target,
        boolean identityAOnLeft,
        boolean mirrored
    ) {
        if (trialId == null || trialId.isEmpty() || batch < 1 || batch > 3 || target == null) {
            throw new IllegalArgumentException("Calibration presentation is invalid");
        }
        this.trialId = trialId;
        this.batch = batch;
        this.target = target;
        this.identityAOnLeft = identityAOnLeft;
        this.mirrored = mirrored;
    }

    String getTrialId() {
        return trialId;
    }

    int getBatch() {
        return batch;
    }

    BaseQualificationTarget getTarget() {
        return target;
    }

    boolean isIdentityAOnLeft() {
        return identityAOnLeft;
    }

    boolean isMirrored() {
        return mirrored;
    }

    Response getCorrectResponse() {
        boolean targetIsA = target.getRegion().isIdentityA();
        return targetIsA == identityAOnLeft ? Response.LEFT : Response.RIGHT;
    }
}
