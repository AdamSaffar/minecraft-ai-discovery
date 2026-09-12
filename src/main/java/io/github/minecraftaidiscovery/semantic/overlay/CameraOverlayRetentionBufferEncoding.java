package io.github.minecraftaidiscovery.semantic.overlay;

import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;

public final class CameraOverlayRetentionBufferEncoding {
    public static final String ENCODING =
        "uint8 retained pre-overlay RGB fraction; 0 fully replaced, 255 unchanged";

    private CameraOverlayRetentionBufferEncoding() {
    }

    public static byte[] requireU8(int width, int height, byte[] values) {
        int pixels = requireDimensions(width, height);
        if (values == null || values.length != pixels) {
            throw new IllegalArgumentException("Camera-overlay retention buffer length is invalid");
        }
        return values;
    }

    public static byte[] maskToPositiveBase(
        int width,
        int height,
        byte[] fullScreenRetention,
        int[] baseClassIds
    ) {
        int pixels = requireDimensions(width, height);
        requireU8(width, height, fullScreenRetention);
        if (baseClassIds == null || baseClassIds.length != pixels) {
            throw new IllegalArgumentException("Base class buffer dimensions do not match");
        }

        byte[] masked = new byte[pixels];
        for (int pixel = 0; pixel < pixels; pixel++) {
            if (baseClassIds[pixel] != BlockClassManifest.BACKGROUND_CLASS_ID) {
                masked[pixel] = fullScreenRetention[pixel];
            }
        }
        return masked;
    }

    public static int composeRetentionU8(int retained, int sourceAlpha) {
        requireU8Value("retention", retained);
        requireU8Value("source alpha", sourceAlpha);
        // Integer reference for repeated source-over destination retention.
        return (retained * (255 - sourceAlpha) + 127) / 255;
    }

    public static int requireDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Camera-overlay retention dimensions must be positive");
        }
        return Math.multiplyExact(width, height);
    }

    private static void requireU8Value(String name, int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " must be in [0,255]");
        }
    }
}
