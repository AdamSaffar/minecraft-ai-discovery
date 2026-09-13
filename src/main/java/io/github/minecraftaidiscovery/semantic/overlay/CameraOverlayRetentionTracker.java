package io.github.minecraftaidiscovery.semantic.overlay;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class CameraOverlayRetentionTracker {
    private final EnumMap<CameraOverlayKind, Integer> drawCounts =
        new EnumMap<>(CameraOverlayKind.class);
    private CameraOverlayKind activeKind;
    private boolean overlayStageEntered;
    private boolean overlayStageEnded;

    public void beginOverlayStage() {
        if (overlayStageEntered || overlayStageEnded) {
            throw new IllegalStateException("Camera-overlay stage cannot be entered twice");
        }
        overlayStageEntered = true;
    }

    public void begin(CameraOverlayKind kind) {
        if (!overlayStageEntered || overlayStageEnded) {
            throw new IllegalStateException("Camera-overlay kind is outside the overlay stage");
        }
        if (kind == null || activeKind != null || drawCounts.containsKey(kind)) {
            throw new IllegalStateException("Camera-overlay kind lifecycle is invalid");
        }
        activeKind = kind;
        drawCounts.put(kind, 0);
    }

    public CameraOverlayKind recordDraw() {
        if (activeKind == null) {
            throw new IllegalStateException("Overlay draw has no active camera-overlay kind");
        }
        drawCounts.put(activeKind, drawCounts.get(activeKind) + 1);
        return activeKind;
    }

    public void end(CameraOverlayKind kind) {
        if (kind == null || activeKind != kind) {
            throw new IllegalStateException("Camera-overlay kind ended out of order");
        }
        int actual = drawCounts.get(kind);
        if (actual != kind.getExpectedDrawCount()) {
            throw new IllegalStateException(
                "Camera-overlay " + kind.getMetadataName() + " emitted " + actual
                    + " draws, expected " + kind.getExpectedDrawCount()
            );
        }
        activeKind = null;
    }

    public void endOverlayStage() {
        if (!overlayStageEntered || overlayStageEnded || activeKind != null) {
            throw new IllegalStateException("Camera-overlay stage lifecycle is invalid");
        }
        overlayStageEnded = true;
    }

    public boolean wasOverlayStageEntered() {
        return overlayStageEntered;
    }

    public Map<CameraOverlayKind, Integer> finish() {
        if (overlayStageEntered != overlayStageEnded || activeKind != null) {
            throw new IllegalStateException("Camera-overlay capture ended with an incomplete stage");
        }
        return Collections.unmodifiableMap(new EnumMap<>(drawCounts));
    }
}
