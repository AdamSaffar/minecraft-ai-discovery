package io.github.minecraftaidiscovery.semantic.visibility;

import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;

public final class BaseInfluenceComposition {
    public static final int RGB_BYTES_PER_PIXEL = 3;
    public static final String RGB_ENCODING =
        "uint8 RGB base-influence coefficient from lighting, fog, foreground transmission, "
            + "and camera-overlay retention";

    private static final long U8_PRODUCT_DENOMINATOR = 255L * 255L * 255L;

    private BaseInfluenceComposition() {
    }

    public static BaseInfluenceCompositionResult compose(
        int width,
        int height,
        int[] baseClassIds,
        byte[] lightingRgb,
        byte[] lightingValidity,
        byte[] fogRetention,
        byte[] accumulatedOpacity,
        byte[] translucentOverflow,
        byte[] cameraOverlayRetention,
        byte[] firstPersonDepthCoverage
    ) {
        int pixels = requireDimensions(width, height);
        requireLength("base classes", pixels, baseClassIds);
        requireLength("lighting RGB", Math.multiplyExact(pixels, RGB_BYTES_PER_PIXEL), lightingRgb);
        requireLength("lighting validity", pixels, lightingValidity);
        requireLength("fog retention", pixels, fogRetention);
        requireLength("accumulated opacity", pixels, accumulatedOpacity);
        requireLength("translucent overflow", pixels, translucentOverflow);
        requireLength("camera-overlay retention", pixels, cameraOverlayRetention);
        requireLength("first-person depth coverage", pixels, firstPersonDepthCoverage);

        byte[] composedRgb = new byte[Math.multiplyExact(pixels, RGB_BYTES_PER_PIXEL)];
        byte[] reasons = new byte[pixels];
        for (int pixel = 0; pixel < pixels; pixel++) {
            int baseClassId = baseClassIds[pixel];
            requireClassId(baseClassId);
            int lightingStatus = requireBinaryU8(
                "lighting validity",
                lightingValidity[pixel]
            );
            int overflow = requireBinaryFlag("translucent overflow", translucentOverflow[pixel]);
            int firstPersonCoverage = requireBinaryU8(
                "first-person depth coverage",
                firstPersonDepthCoverage[pixel]
            );

            if (baseClassId == BlockClassManifest.BACKGROUND_CLASS_ID) {
                requireBackgroundMask(
                    pixel,
                    lightingStatus,
                    lightingRgb,
                    fogRetention,
                    cameraOverlayRetention,
                    firstPersonCoverage
                );
                continue;
            }

            int reason = BaseInfluenceReasonEncoding.BACKGROUND;
            if (lightingStatus == 0) {
                reason |= BaseInfluenceReasonEncoding.UNSUPPORTED_LIGHTING;
            }
            if (overflow != 0) {
                reason |= BaseInfluenceReasonEncoding.TRANSLUCENT_OVERFLOW;
            }
            // A depth write can still be blended, so it cannot be converted into retention here.
            if (firstPersonCoverage != 0) {
                reason |= BaseInfluenceReasonEncoding.FIRST_PERSON_RETENTION_UNSUPPORTED;
            }
            if (reason != BaseInfluenceReasonEncoding.BACKGROUND) {
                reasons[pixel] = (byte)reason;
                continue;
            }

            reasons[pixel] = (byte)BaseInfluenceReasonEncoding.SUPPORTED;
            int fog = Byte.toUnsignedInt(fogRetention[pixel]);
            int transmission = 255 - Byte.toUnsignedInt(accumulatedOpacity[pixel]);
            int overlay = Byte.toUnsignedInt(cameraOverlayRetention[pixel]);
            int rgbOffset = pixel * RGB_BYTES_PER_PIXEL;
            for (int channel = 0; channel < RGB_BYTES_PER_PIXEL; channel++) {
                int lighting = Byte.toUnsignedInt(lightingRgb[rgbOffset + channel]);
                composedRgb[rgbOffset + channel] = (byte)multiplyU8Factors(
                    lighting,
                    fog,
                    transmission,
                    overlay
                );
            }
        }
        return new BaseInfluenceCompositionResult(width, height, composedRgb, reasons);
    }

    static int multiplyU8Factors(int first, int second, int third, int fourth) {
        requireUnsigned8("first factor", first);
        requireUnsigned8("second factor", second);
        requireUnsigned8("third factor", third);
        requireUnsigned8("fourth factor", fourth);
        // Round once after the full 64-bit product. Repeated uint8 rounding would depend on order.
        long numerator = (long)first * second * third * fourth;
        return (int)((numerator + U8_PRODUCT_DENOMINATOR / 2L) / U8_PRODUCT_DENOMINATOR);
    }

    static int requireDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Base-influence dimensions must be positive");
        }
        return Math.multiplyExact(width, height);
    }

    private static void requireBackgroundMask(
        int pixel,
        int lightingStatus,
        byte[] lightingRgb,
        byte[] fogRetention,
        byte[] cameraOverlayRetention,
        int firstPersonCoverage
    ) {
        int rgbOffset = pixel * RGB_BYTES_PER_PIXEL;
        if (lightingStatus != 0
            || Byte.toUnsignedInt(lightingRgb[rgbOffset]) != 0
            || Byte.toUnsignedInt(lightingRgb[rgbOffset + 1]) != 0
            || Byte.toUnsignedInt(lightingRgb[rgbOffset + 2]) != 0
            || Byte.toUnsignedInt(fogRetention[pixel]) != 0
            || firstPersonCoverage != 0
            || Byte.toUnsignedInt(cameraOverlayRetention[pixel]) != 0) {
            throw new IllegalArgumentException(
                "Base-masked diagnostic data exists on background pixel " + pixel
            );
        }
    }

    private static int requireBinaryU8(String name, byte value) {
        int unsigned = Byte.toUnsignedInt(value);
        if (unsigned != 0 && unsigned != 0xFF) {
            throw new IllegalArgumentException(name + " must contain only 0 or 255");
        }
        return unsigned;
    }

    private static int requireBinaryFlag(String name, byte value) {
        int unsigned = Byte.toUnsignedInt(value);
        if (unsigned != 0 && unsigned != 1) {
            throw new IllegalArgumentException(name + " must contain only 0 or 1");
        }
        return unsigned;
    }

    private static void requireClassId(int classId) {
        if (classId < BlockClassManifest.BACKGROUND_CLASS_ID
            || classId > BlockClassManifest.MAX_SEMANTIC_CLASS_ID) {
            throw new IllegalArgumentException(
                "Base class is outside the unsigned 16-bit range: " + classId
            );
        }
    }

    private static void requireUnsigned8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(name + " must be in [0,255]");
        }
    }

    private static void requireLength(String name, int expected, byte[] values) {
        if (values == null || values.length != expected) {
            throw new IllegalArgumentException(name + " buffer length is invalid");
        }
    }

    private static void requireLength(String name, int expected, int[] values) {
        if (values == null || values.length != expected) {
            throw new IllegalArgumentException(name + " buffer length is invalid");
        }
    }
}
