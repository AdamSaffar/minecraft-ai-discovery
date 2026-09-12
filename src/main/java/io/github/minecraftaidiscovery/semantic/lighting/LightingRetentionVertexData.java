package io.github.minecraftaidiscovery.semantic.lighting;

import java.util.Arrays;

public final class LightingRetentionVertexData {
    // RGB contain the same AO/directional multiplier. Alpha is a validity guard.
    public static final int BYTES_PER_VERTEX = 4;

    private static final int INITIAL_VERTEX_CAPACITY = 256;
    private static final int VALID = 0xFF;

    private byte[] data = new byte[INITIAL_VERTEX_CAPACITY * BYTES_PER_VERTEX];
    private int readOffset;
    private int writeOffset;

    public void append(LightingRetentionVertexEncoding.Sample sample) {
        if (sample == null) {
            throw new IllegalArgumentException("Lighting-retention vertex sample is missing");
        }
        ensureCapacity(BYTES_PER_VERTEX);
        int value = sample.getMultiplier();
        data[writeOffset] = (byte)value;
        data[writeOffset + 1] = (byte)value;
        data[writeOffset + 2] = (byte)value;
        data[writeOffset + 3] = sample.isValid() ? (byte)VALID : 0;
        writeOffset += BYTES_PER_VERTEX;
    }

    public byte[] takeVertices(int vertexCount) {
        if (vertexCount < 0) {
            throw new IllegalArgumentException("Vertex count must be nonnegative");
        }
        int byteCount = Math.multiplyExact(vertexCount, BYTES_PER_VERTEX);
        if (writeOffset == readOffset) {
            return new byte[byteCount];
        }
        if (writeOffset - readOffset < byteCount) {
            throw new IllegalStateException(
                "Lighting-retention stream has "
                    + ((writeOffset - readOffset) / BYTES_PER_VERTEX)
                    + " vertices but the renderer uploaded " + vertexCount
            );
        }

        byte[] result = Arrays.copyOfRange(data, readOffset, readOffset + byteCount);
        readOffset += byteCount;
        if (readOffset == writeOffset) {
            clear();
        }
        validate(result);
        return result;
    }

    public void clear() {
        readOffset = 0;
        writeOffset = 0;
    }

    public static boolean isInvalidOnly(byte[] encodedVertices) {
        validate(encodedVertices);
        for (int offset = 0; offset < encodedVertices.length; offset += BYTES_PER_VERTEX) {
            if (isValidAt(encodedVertices, offset)) {
                return false;
            }
        }
        return true;
    }

    public static void requireCompatibleVertexCount(byte[] lightingVertices, int vertexCount) {
        validate(lightingVertices);
        if (vertexCount < 0 || lightingVertices.length / BYTES_PER_VERTEX != vertexCount) {
            throw new IllegalStateException(
                "Lighting-retention sidecar has a different vertex count"
            );
        }
    }

    public static int multiplierAt(byte[] encodedVertices, int offset) {
        validateVertexOffset(encodedVertices, offset);
        int red = Byte.toUnsignedInt(encodedVertices[offset]);
        int green = Byte.toUnsignedInt(encodedVertices[offset + 1]);
        int blue = Byte.toUnsignedInt(encodedVertices[offset + 2]);
        int validity = Byte.toUnsignedInt(encodedVertices[offset + 3]);
        if (red != green || red != blue) {
            throw new IllegalStateException("Lighting-retention multiplier channels disagree");
        }
        if (validity != 0 && validity != VALID) {
            throw new IllegalStateException("Lighting-retention validity guard is malformed");
        }
        if (validity == 0 && red != 0) {
            throw new IllegalStateException("Invalid lighting-retention vertex has a nonzero value");
        }
        return red;
    }

    public static boolean isValidAt(byte[] encodedVertices, int offset) {
        multiplierAt(encodedVertices, offset);
        return Byte.toUnsignedInt(encodedVertices[offset + 3]) == VALID;
    }

    private static void validate(byte[] encodedVertices) {
        if (encodedVertices == null || encodedVertices.length % BYTES_PER_VERTEX != 0) {
            throw new IllegalArgumentException("Lighting-retention vertex data is malformed");
        }
        for (int offset = 0; offset < encodedVertices.length; offset += BYTES_PER_VERTEX) {
            multiplierAtWithoutRecursiveValidation(encodedVertices, offset);
        }
    }

    private static void multiplierAtWithoutRecursiveValidation(byte[] encodedVertices, int offset) {
        int red = Byte.toUnsignedInt(encodedVertices[offset]);
        int green = Byte.toUnsignedInt(encodedVertices[offset + 1]);
        int blue = Byte.toUnsignedInt(encodedVertices[offset + 2]);
        int validity = Byte.toUnsignedInt(encodedVertices[offset + 3]);
        if (red != green || red != blue
            || (validity != 0 && validity != VALID)
            || (validity == 0 && red != 0)) {
            throw new IllegalStateException("Lighting-retention vertex record is malformed");
        }
    }

    private static void validateVertexOffset(byte[] encodedVertices, int offset) {
        if (encodedVertices == null
            || offset < 0
            || offset % BYTES_PER_VERTEX != 0
            || offset + BYTES_PER_VERTEX > encodedVertices.length) {
            throw new IllegalArgumentException("Invalid lighting-retention vertex offset");
        }
    }

    private void ensureCapacity(int additionalBytes) {
        int required = Math.addExact(writeOffset, additionalBytes);
        if (required <= data.length) {
            return;
        }
        int expanded = Math.max(required, Math.multiplyExact(data.length, 2));
        data = Arrays.copyOf(data, expanded);
    }
}
