package io.github.minecraftaidiscovery.semantic;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class SemanticVertexDataTest {
    @Test
    public void preservesClassIdsAbove255InDedicatedVertexCodes() {
        SemanticVertexData data = new SemanticVertexData();
        for (int vertex = 0; vertex < 4; vertex++) {
            data.appendClassId(662);
        }

        byte[] encoded = data.takeVertices(4);

        assertEquals(16, encoded.length);
        assertArrayEquals(
            new byte[] {
                (byte)0x96, 0x02, 0x00, (byte)0xFF,
                (byte)0x96, 0x02, 0x00, (byte)0xFF,
                (byte)0x96, 0x02, 0x00, (byte)0xFF,
                (byte)0x96, 0x02, 0x00, (byte)0xFF
            },
            encoded
        );
        SemanticVertexData.requireConstantQuadClassIds(encoded);
    }

    @Test
    public void preservesBackgroundAndRejectsMixedOrIncompleteQuads() {
        SemanticVertexData data = new SemanticVertexData();
        for (int vertex = 0; vertex < 4; vertex++) {
            data.appendClassId(0);
        }
        SemanticVertexData.requireConstantQuadClassIds(data.takeVertices(4));
        assertTrue(SemanticVertexData.isBackgroundOnly(new SemanticVertexData().takeVertices(4)));

        byte[] mixed = new byte[16];
        for (int vertex = 0; vertex < 4; vertex++) {
            mixed[vertex * 4 + 3] = (byte)0xFF;
        }
        mixed[4] = 1;
        expectIllegalState(
            () -> SemanticVertexData.requireConstantQuadClassIds(mixed),
            "more than one class ID"
        );
        expectIllegalState(
            () -> SemanticVertexData.requireConstantQuadClassIds(new byte[4]),
            "not complete quads"
        );
    }

    @Test
    public void restoresNestedClassEncodingState() {
        assertEquals(SemanticVertexEncoding.INACTIVE, SemanticVertexEncoding.getRawActiveClassId());
        int inactive = SemanticVertexEncoding.beginClassId(139);
        try {
            int previous = SemanticVertexEncoding.beginClassId(444);

            assertEquals(SemanticVertexEncoding.INACTIVE, inactive);
            assertEquals(139, previous);
            assertEquals(444, SemanticVertexEncoding.getActiveClassId());

            SemanticVertexEncoding.restore(previous);
            assertEquals(139, SemanticVertexEncoding.getActiveClassId());
        } finally {
            SemanticVertexEncoding.restore(inactive);
        }
        assertEquals(SemanticVertexEncoding.INACTIVE, SemanticVertexEncoding.getRawActiveClassId());
        assertEquals(0, SemanticVertexEncoding.getActiveClassId());
    }

    @Test
    public void failsWhenTheSemanticSidecarHasFewerVerticesThanTheUpload() {
        SemanticVertexData data = new SemanticVertexData();
        for (int vertex = 0; vertex < 3; vertex++) {
            data.appendClassId(447);
        }

        expectIllegalState(
            () -> data.takeVertices(4),
            "has 3 vertices but the renderer uploaded 4"
        );
    }

    private static void expectIllegalState(ThrowingRunnable runnable, String expectedMessage) {
        try {
            runnable.run();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains(expectedMessage));
        }
    }

    private interface ThrowingRunnable {
        void run();
    }
}
