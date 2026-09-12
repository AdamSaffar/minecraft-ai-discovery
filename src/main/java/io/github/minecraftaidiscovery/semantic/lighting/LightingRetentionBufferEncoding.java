package io.github.minecraftaidiscovery.semantic.lighting;

import io.github.minecraftaidiscovery.semantic.SemanticClassEncoding;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;

public final class LightingRetentionBufferEncoding {
    public static final int RGB_BYTES_PER_PIXEL = 3;
    public static final String RGB_ENCODING =
        "uint8 RGB active-lightmap response times renderer AO/directional multiplier";
    public static final String VALIDITY_ENCODING = "uint8 validity; 255 valid base, 0 background";

    private LightingRetentionBufferEncoding() {
    }

    public static int expectedRgbLength(int width, int height) {
        return Math.multiplyExact(pixelCount(width, height), RGB_BYTES_PER_PIXEL);
    }

    public static byte[] requireRgb(int width, int height, byte[] values) {
        int expected = expectedRgbLength(width, height);
        if (values == null || values.length != expected) {
            throw new IllegalArgumentException(
                "Expected " + expected + " lighting-retention RGB bytes"
            );
        }
        return values;
    }

    public static byte[] validityForSupportedBase(
        int width,
        int height,
        int[] baseClassIds,
        byte[] renderedAlpha
    ) {
        int pixels = pixelCount(width, height);
        if (baseClassIds == null
            || baseClassIds.length != pixels
            || renderedAlpha == null
            || renderedAlpha.length != pixels) {
            throw new IllegalArgumentException("Lighting validity inputs do not match dimensions");
        }
        byte[] validity = new byte[pixels];
        for (int pixel = 0; pixel < pixels; pixel++) {
            boolean basePresent = baseClassIds[pixel]
                != BlockClassManifest.BACKGROUND_CLASS_ID;
            boolean lightingPresent = Byte.toUnsignedInt(renderedAlpha[pixel])
                >= SemanticClassEncoding.MIN_VISIBLE_CUTOUT_ALPHA;
            if (!basePresent && lightingPresent) {
                throw new IllegalStateException(
                    "Lighting retention exists without a winning base fragment"
                );
            }
            if (basePresent && lightingPresent) {
                validity[pixel] = (byte)0xFF;
            }
        }
        return validity;
    }

    public static float reconstructNormalizedResponse(int multiplierByte, int lightmapByte) {
        requireUnsigned8("multiplier", multiplierByte);
        requireUnsigned8("lightmap channel", lightmapByte);
        return (multiplierByte / 255.0F) * (lightmapByte / 255.0F);
    }

    private static int pixelCount(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Lighting-retention dimensions must be positive");
        }
        return Math.multiplyExact(width, height);
    }

    private static void requireUnsigned8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(name + " is outside the uint8 range: " + value);
        }
    }
}
