package io.github.minecraftaidiscovery.semantic.firstperson;

import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;

public final class FirstPersonDepthCoverageEncoding {
    public static final String ENCODING =
        "uint8 binary post-hand depth coverage; 0 clear, 255 depth written";
    public static final float CLEAR_DEPTH = 1.0F;

    private FirstPersonDepthCoverageEncoding() {
    }

    public static byte[] encodeBottomToTopDepth(
        int width,
        int height,
        float[] bottomToTopDepth
    ) {
        int pixels = requireDimensions(width, height);
        if (bottomToTopDepth == null || bottomToTopDepth.length != pixels) {
            throw new IllegalArgumentException("First-person depth buffer length is invalid");
        }

        byte[] topToBottomCoverage = new byte[pixels];
        for (int sourceY = 0; sourceY < height; sourceY++) {
            int destinationY = height - sourceY - 1;
            for (int x = 0; x < width; x++) {
                float depth = bottomToTopDepth[sourceY * width + x];
                requireDepth(depth);
                if (depth < CLEAR_DEPTH) {
                    topToBottomCoverage[destinationY * width + x] = (byte)0xFF;
                }
            }
        }
        return topToBottomCoverage;
    }

    public static byte[] requireBinary(int width, int height, byte[] coverage) {
        int pixels = requireDimensions(width, height);
        if (coverage == null || coverage.length != pixels) {
            throw new IllegalArgumentException(
                "First-person depth-coverage buffer length is invalid"
            );
        }
        for (byte value : coverage) {
            int unsigned = value & 0xFF;
            if (unsigned != 0 && unsigned != 0xFF) {
                throw new IllegalArgumentException(
                    "First-person depth coverage must be binary"
                );
            }
        }
        return coverage;
    }

    public static byte[] maskToPositiveBase(
        int width,
        int height,
        byte[] fullScreenCoverage,
        int[] baseClassIds
    ) {
        int pixels = requireDimensions(width, height);
        requireBinary(width, height, fullScreenCoverage);
        if (baseClassIds == null || baseClassIds.length != pixels) {
            throw new IllegalArgumentException("Base class buffer dimensions do not match");
        }

        byte[] masked = new byte[pixels];
        for (int pixel = 0; pixel < pixels; pixel++) {
            if (baseClassIds[pixel] != BlockClassManifest.BACKGROUND_CLASS_ID) {
                masked[pixel] = fullScreenCoverage[pixel];
            }
        }
        return masked;
    }

    public static long countCovered(byte[] coverage) {
        if (coverage == null) {
            throw new IllegalArgumentException("First-person depth coverage is missing");
        }
        long count = 0;
        for (byte value : coverage) {
            int unsigned = value & 0xFF;
            if (unsigned == 0xFF) {
                count++;
            } else if (unsigned != 0) {
                throw new IllegalArgumentException(
                    "First-person depth coverage must be binary"
                );
            }
        }
        return count;
    }

    public static int requireDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                "First-person depth-coverage dimensions must be positive"
            );
        }
        return Math.multiplyExact(width, height);
    }

    private static void requireDepth(float depth) {
        if (!Float.isFinite(depth) || depth < 0.0F || depth > CLEAR_DEPTH) {
            throw new IllegalArgumentException(
                "First-person depth sample must be finite and in [0,1]"
            );
        }
    }
}
