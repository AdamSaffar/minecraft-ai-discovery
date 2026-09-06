package io.github.minecraftaidiscovery.semantic;

import java.util.Arrays;

public final class SemanticVertexData {
    public static final int BYTES_PER_VERTEX = 4;

    private static final int INITIAL_VERTEX_CAPACITY = 256;

    private byte[] data = new byte[INITIAL_VERTEX_CAPACITY * BYTES_PER_VERTEX];
    private int readOffset;
    private int writeOffset;

    public void appendClassId(int classId) {
        SemanticClassEncoding.requireUnsigned16(classId);
        ensureCapacity(BYTES_PER_VERTEX);
        data[writeOffset] = (byte)(classId & 0xFF);
        data[writeOffset + 1] = (byte)(classId >>> 8);
        data[writeOffset + 2] = 0;
        data[writeOffset + 3] = (byte)0xFF;
        writeOffset += BYTES_PER_VERTEX;
    }

    public byte[] takeVertices(int vertexCount) {
        if (vertexCount < 0) {
            throw new IllegalArgumentException("Vertex count must be nonnegative");
        }
        int byteCount = Math.multiplyExact(vertexCount, BYTES_PER_VERTEX);
        if (writeOffset == readOffset) {
            byte[] background = new byte[byteCount];
            for (int offset = 0; offset < background.length; offset += BYTES_PER_VERTEX) {
                background[offset + 3] = (byte)0xFF;
            }
            return background;
        }
        if (writeOffset - readOffset < byteCount) {
            throw new IllegalStateException(
                "Semantic vertex stream has " + ((writeOffset - readOffset) / BYTES_PER_VERTEX)
                    + " vertices but the renderer uploaded " + vertexCount
            );
        }

        byte[] result = Arrays.copyOfRange(data, readOffset, readOffset + byteCount);
        readOffset += byteCount;
        if (readOffset == writeOffset) {
            clear();
        }
        return result;
    }

    public static boolean isBackgroundOnly(byte[] encodedVertices) {
        if (encodedVertices == null || encodedVertices.length % BYTES_PER_VERTEX != 0) {
            throw new IllegalArgumentException("Semantic vertex data is malformed");
        }
        for (int offset = 0; offset < encodedVertices.length; offset += BYTES_PER_VERTEX) {
            if (classIdAt(encodedVertices, offset) != 0) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        readOffset = 0;
        writeOffset = 0;
    }

    public static void requireConstantQuadClassIds(byte[] encodedVertices) {
        if (encodedVertices == null || encodedVertices.length % (BYTES_PER_VERTEX * 4) != 0) {
            throw new IllegalStateException("Semantic vertex data is not complete quads");
        }
        for (int quadOffset = 0; quadOffset < encodedVertices.length; quadOffset += BYTES_PER_VERTEX * 4) {
            int classId = classIdAt(encodedVertices, quadOffset);
            for (int vertex = 1; vertex < 4; vertex++) {
                int vertexOffset = quadOffset + vertex * BYTES_PER_VERTEX;
                if (classIdAt(encodedVertices, vertexOffset) != classId) {
                    throw new IllegalStateException(
                        "A semantic quad contains more than one class ID"
                    );
                }
            }
        }
    }

    private static int classIdAt(byte[] encodedVertices, int offset) {
        if (encodedVertices[offset + 2] != 0 || Byte.toUnsignedInt(encodedVertices[offset + 3]) != 0xFF) {
            throw new IllegalStateException("Semantic vertex code has invalid guard channels");
        }
        return SemanticClassEncoding.decodeGpuBytes(
            Byte.toUnsignedInt(encodedVertices[offset]),
            Byte.toUnsignedInt(encodedVertices[offset + 1])
        );
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
