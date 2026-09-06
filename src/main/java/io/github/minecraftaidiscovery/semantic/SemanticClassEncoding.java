package io.github.minecraftaidiscovery.semantic;

import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;

public final class SemanticClassEncoding {
    public static final String CLASS_BUFFER_ENCODING = "uint16 little-endian class id";
    public static final String BYTE_ORDER = "little-endian";
    public static final String PIXEL_ORDER = "row-major, top-left origin";
    public static final int BYTES_PER_PIXEL = 2;

    private static final int FALSE_COLOR_MULTIPLIER = 0x9E3779;
    private static final int FALSE_COLOR_OFFSET = 0x7F4A7C;

    private SemanticClassEncoding() {
    }

    public static byte[] encodeU16Le(int width, int height, int[] classIds) {
        int pixelCount = pixelCount(width, height);
        if (classIds == null) {
            throw new IllegalArgumentException("Semantic class IDs are missing");
        }
        if (classIds.length != pixelCount) {
            throw new IllegalArgumentException(
                "Expected " + pixelCount + " semantic class IDs but found " + classIds.length
            );
        }

        byte[] encoded = new byte[Math.multiplyExact(pixelCount, BYTES_PER_PIXEL)];
        for (int index = 0; index < classIds.length; index++) {
            int classId = requireUnsigned16(classIds[index]);
            encoded[index * BYTES_PER_PIXEL] = (byte)(classId & 0xFF);
            encoded[index * BYTES_PER_PIXEL + 1] = (byte)(classId >>> 8);
        }
        return encoded;
    }

    public static int[] decodeU16Le(int width, int height, byte[] encoded) {
        int pixelCount = pixelCount(width, height);
        int expectedLength = Math.multiplyExact(pixelCount, BYTES_PER_PIXEL);
        if (encoded == null) {
            throw new IllegalArgumentException("Encoded semantic class buffer is missing");
        }
        if (encoded.length != expectedLength) {
            throw new IllegalArgumentException(
                "Expected " + expectedLength + " class-buffer bytes but found " + encoded.length
            );
        }

        int[] classIds = new int[pixelCount];
        for (int index = 0; index < pixelCount; index++) {
            classIds[index] = decodeGpuBytes(
                Byte.toUnsignedInt(encoded[index * BYTES_PER_PIXEL]),
                Byte.toUnsignedInt(encoded[index * BYTES_PER_PIXEL + 1])
            );
        }
        return classIds;
    }

    public static int decodeGpuBytes(int lowByte, int highByte) {
        requireUnsigned8("lowByte", lowByte);
        requireUnsigned8("highByte", highByte);
        return lowByte | highByte << 8;
    }

    public static int falseColorRgb(int classId) {
        requireUnsigned16(classId);
        if (classId == BlockClassManifest.BACKGROUND_CLASS_ID) {
            return 0;
        }
        return (classId * FALSE_COLOR_MULTIPLIER + FALSE_COLOR_OFFSET) & 0xFFFFFF;
    }

    public static int expectedBufferLength(int width, int height) {
        return Math.multiplyExact(pixelCount(width, height), BYTES_PER_PIXEL);
    }

    public static int requireUnsigned16(int classId) {
        if (classId < 0 || classId > BlockClassManifest.MAX_SEMANTIC_CLASS_ID) {
            throw new IllegalArgumentException("Semantic class ID is outside the uint16 range: " + classId);
        }
        return classId;
    }

    private static int pixelCount(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Capture dimensions must be positive");
        }
        return Math.multiplyExact(width, height);
    }

    private static void requireUnsigned8(String name, int value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(name + " is outside the uint8 range: " + value);
        }
    }
}
