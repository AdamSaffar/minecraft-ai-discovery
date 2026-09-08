package io.github.minecraftaidiscovery.semantic.translucent;

import io.github.minecraftaidiscovery.semantic.SemanticClassEncoding;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;
import java.util.Arrays;

public final class TranslucentProbeVertexData {
    // low class byte, high class byte, source kind, guard
    public static final int BYTES_PER_VERTEX = 4;

    private static final int INITIAL_VERTEX_CAPACITY = 256;
    private static final int GUARD = 0xFF;

    private byte[] data = new byte[INITIAL_VERTEX_CAPACITY * BYTES_PER_VERTEX];
    private int readOffset;
    private int writeOffset;

    public void append(TranslucentProbeVertexEncoding.Contribution contribution) {
        if (contribution == null) {
            throw new IllegalArgumentException("Translucent probe contribution is missing");
        }
        append(contribution.getClassId(), contribution.getSource());
    }

    public void append(int classId, TranslucentProbeSource source) {
        SemanticClassEncoding.requireUnsigned16(classId);
        if (source == null) {
            throw new IllegalArgumentException("Translucent probe source is missing");
        }
        if ((classId == BlockClassManifest.BACKGROUND_CLASS_ID)
            != (source == TranslucentProbeSource.NONE)) {
            throw new IllegalArgumentException(
                "Background class and source-kind sentinel must be used together"
            );
        }

        ensureCapacity(BYTES_PER_VERTEX);
        data[writeOffset] = (byte)(classId & 0xFF);
        data[writeOffset + 1] = (byte)(classId >>> 8);
        data[writeOffset + 2] = (byte)source.getCode();
        data[writeOffset + 3] = (byte)GUARD;
        writeOffset += BYTES_PER_VERTEX;
    }

    public byte[] takeVertices(int vertexCount) {
        int byteCount = checkedByteCount(vertexCount);
        if (writeOffset == readOffset) {
            return null;
        }
        requireAvailable(byteCount, vertexCount);

        byte[] result = Arrays.copyOfRange(data, readOffset, readOffset + byteCount);
        readOffset += byteCount;
        if (readOffset == writeOffset) {
            clear();
        }
        return result;
    }

    public byte[] snapshotLastVertices(int vertexCount) {
        int byteCount = checkedByteCount(vertexCount);
        if (writeOffset == readOffset) {
            return null;
        }
        requireAvailable(byteCount, vertexCount);
        return Arrays.copyOfRange(data, writeOffset - byteCount, writeOffset);
    }

    public void restoreSnapshot(byte[] snapshot) {
        clear();
        if (snapshot == null) {
            return;
        }
        validate(snapshot);
        ensureCapacity(snapshot.length);
        System.arraycopy(snapshot, 0, data, 0, snapshot.length);
        writeOffset = snapshot.length;
    }

    public void reorderLastQuads(int[] sortedOriginalIndices) {
        if (writeOffset == readOffset) {
            return;
        }
        validatePermutation(sortedOriginalIndices);
        int quadBytes = BYTES_PER_VERTEX * 4;
        int byteCount = Math.multiplyExact(sortedOriginalIndices.length, quadBytes);
        if (writeOffset - readOffset < byteCount) {
            throw new IllegalStateException("Translucent probe stream is shorter than the sorted quad set");
        }

        // BufferBuilder applies this permutation to complete quads, so the sidecar must use the same source indices.
        int start = writeOffset - byteCount;
        byte[] unsorted = Arrays.copyOfRange(data, start, writeOffset);
        for (int targetQuad = 0; targetQuad < sortedOriginalIndices.length; targetQuad++) {
            int sourceQuad = sortedOriginalIndices[targetQuad];
            System.arraycopy(
                unsorted,
                sourceQuad * quadBytes,
                data,
                start + targetQuad * quadBytes,
                quadBytes
            );
        }
    }

    public void clear() {
        readOffset = 0;
        writeOffset = 0;
    }

    public static boolean isBackgroundOnly(byte[] encodedVertices) {
        validate(encodedVertices);
        for (int offset = 0; offset < encodedVertices.length; offset += BYTES_PER_VERTEX) {
            if (classIdAt(encodedVertices, offset) != BlockClassManifest.BACKGROUND_CLASS_ID) {
                return false;
            }
        }
        return true;
    }

    public static void requireConstantQuadContributions(byte[] encodedVertices) {
        validate(encodedVertices);
        int quadBytes = BYTES_PER_VERTEX * 4;
        if (encodedVertices.length % quadBytes != 0) {
            throw new IllegalStateException("Translucent probe vertex data is not complete quads");
        }
        for (int quadOffset = 0; quadOffset < encodedVertices.length; quadOffset += quadBytes) {
            for (int vertex = 1; vertex < 4; vertex++) {
                int vertexOffset = quadOffset + vertex * BYTES_PER_VERTEX;
                for (int field = 0; field < BYTES_PER_VERTEX; field++) {
                    if (encodedVertices[vertexOffset + field] != encodedVertices[quadOffset + field]) {
                        throw new IllegalStateException(
                            "A translucent probe quad contains more than one contribution"
                        );
                    }
                }
            }
        }
    }

    public static int classIdAt(byte[] encodedVertices, int offset) {
        validateVertexOffset(encodedVertices, offset);
        if (Byte.toUnsignedInt(encodedVertices[offset + 3]) != GUARD) {
            throw new IllegalStateException("Translucent probe vertex code has an invalid guard byte");
        }
        int classId = SemanticClassEncoding.decodeGpuBytes(
            Byte.toUnsignedInt(encodedVertices[offset]),
            Byte.toUnsignedInt(encodedVertices[offset + 1])
        );
        TranslucentProbeSource source = sourceAt(encodedVertices, offset);
        if ((classId == BlockClassManifest.BACKGROUND_CLASS_ID)
            != (source == TranslucentProbeSource.NONE)) {
            throw new IllegalStateException("Translucent probe class/source sentinel mismatch");
        }
        return classId;
    }

    public static TranslucentProbeSource sourceAt(byte[] encodedVertices, int offset) {
        validateVertexOffset(encodedVertices, offset);
        return TranslucentProbeSource.fromCode(Byte.toUnsignedInt(encodedVertices[offset + 2]));
    }

    private static void validate(byte[] encodedVertices) {
        if (encodedVertices == null || encodedVertices.length % BYTES_PER_VERTEX != 0) {
            throw new IllegalArgumentException("Translucent probe vertex data is malformed");
        }
        for (int offset = 0; offset < encodedVertices.length; offset += BYTES_PER_VERTEX) {
            classIdAtWithoutRecursiveValidation(encodedVertices, offset);
        }
    }

    private static void classIdAtWithoutRecursiveValidation(byte[] encodedVertices, int offset) {
        if (Byte.toUnsignedInt(encodedVertices[offset + 3]) != GUARD) {
            throw new IllegalStateException("Translucent probe vertex code has an invalid guard byte");
        }
        int classId = SemanticClassEncoding.decodeGpuBytes(
            Byte.toUnsignedInt(encodedVertices[offset]),
            Byte.toUnsignedInt(encodedVertices[offset + 1])
        );
        TranslucentProbeSource source = TranslucentProbeSource.fromCode(
            Byte.toUnsignedInt(encodedVertices[offset + 2])
        );
        if ((classId == BlockClassManifest.BACKGROUND_CLASS_ID)
            != (source == TranslucentProbeSource.NONE)) {
            throw new IllegalStateException("Translucent probe class/source sentinel mismatch");
        }
    }

    private static void validateVertexOffset(byte[] encodedVertices, int offset) {
        if (encodedVertices == null
            || offset < 0
            || offset % BYTES_PER_VERTEX != 0
            || offset + BYTES_PER_VERTEX > encodedVertices.length) {
            throw new IllegalArgumentException("Invalid translucent probe vertex offset");
        }
    }

    private static void validatePermutation(int[] indices) {
        if (indices == null) {
            throw new IllegalArgumentException("Translucent quad permutation is missing");
        }
        boolean[] present = new boolean[indices.length];
        for (int index : indices) {
            if (index < 0 || index >= indices.length || present[index]) {
                throw new IllegalArgumentException("Translucent quad permutation is invalid");
            }
            present[index] = true;
        }
    }

    private int checkedByteCount(int vertexCount) {
        if (vertexCount < 0) {
            throw new IllegalArgumentException("Vertex count must be nonnegative");
        }
        return Math.multiplyExact(vertexCount, BYTES_PER_VERTEX);
    }

    private void requireAvailable(int byteCount, int vertexCount) {
        if (writeOffset - readOffset < byteCount) {
            throw new IllegalStateException(
                "Translucent probe stream has " + ((writeOffset - readOffset) / BYTES_PER_VERTEX)
                    + " vertices but the renderer requested " + vertexCount
            );
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
