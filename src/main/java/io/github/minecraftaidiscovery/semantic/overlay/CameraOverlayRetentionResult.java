package io.github.minecraftaidiscovery.semantic.overlay;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class CameraOverlayRetentionResult {
    private final int width;
    private final int height;
    private final byte[] fullScreenRetention;
    private final boolean overlayStageEntered;
    private final EnumMap<CameraOverlayKind, Integer> drawCounts;

    public CameraOverlayRetentionResult(
        int width,
        int height,
        byte[] fullScreenRetention,
        boolean overlayStageEntered,
        Map<CameraOverlayKind, Integer> drawCounts
    ) {
        CameraOverlayRetentionBufferEncoding.requireU8(width, height, fullScreenRetention);
        if (drawCounts == null) {
            throw new IllegalArgumentException("Camera-overlay draw counts are missing");
        }
        EnumMap<CameraOverlayKind, Integer> checked = new EnumMap<>(CameraOverlayKind.class);
        for (Map.Entry<CameraOverlayKind, Integer> entry : drawCounts.entrySet()) {
            CameraOverlayKind kind = entry.getKey();
            Integer count = entry.getValue();
            if (kind == null || count == null || count != kind.getExpectedDrawCount()) {
                throw new IllegalArgumentException("Camera-overlay draw count is invalid");
            }
            checked.put(kind, count);
        }
        if (!overlayStageEntered && !checked.isEmpty()) {
            throw new IllegalArgumentException("Draws cannot exist without an overlay stage");
        }
        this.width = width;
        this.height = height;
        this.fullScreenRetention = fullScreenRetention.clone();
        this.overlayStageEntered = overlayStageEntered;
        this.drawCounts = checked;
    }

    public static CameraOverlayRetentionResult noOverlayStage(int width, int height) {
        byte[] retention = new byte[
            CameraOverlayRetentionBufferEncoding.requireDimensions(width, height)
        ];
        Arrays.fill(retention, (byte)0xFF);
        return new CameraOverlayRetentionResult(
            width,
            height,
            retention,
            false,
            Collections.emptyMap()
        );
    }

    public byte[] copyFullScreenRetention() {
        return fullScreenRetention.clone();
    }

    public boolean wasOverlayStageEntered() {
        return overlayStageEntered;
    }

    public Map<CameraOverlayKind, Integer> getDrawCounts() {
        return Collections.unmodifiableMap(drawCounts);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
