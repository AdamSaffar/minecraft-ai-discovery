package io.github.minecraftaidiscovery.semantic.lighting;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class LightingRetentionVertexDataTest {
    @Test
    public void capturesFourDistinctSmoothLightingMultipliersInVertexOrder() {
        LightingRetentionVertexData data = new LightingRetentionVertexData();

        LightingRetentionVertexEncoding.runEnabledQuad(
            new float[] {0.0F, 0.5F, 0.8F, 1.0F},
            () -> {
                for (int vertex = 0; vertex < 4; vertex++) {
                    data.append(LightingRetentionVertexEncoding.consumeVertex());
                }
            }
        );

        byte[] encoded = data.takeVertices(4);
        assertArrayEquals(
            new byte[] {
                0, 0, 0, (byte)0xFF,
                127, 127, 127, (byte)0xFF,
                (byte)204, (byte)204, (byte)204, (byte)0xFF,
                (byte)255, (byte)255, (byte)255, (byte)0xFF
            },
            encoded
        );
        assertEquals(127, LightingRetentionVertexData.multiplierAt(encoded, 4));
        assertTrue(LightingRetentionVertexData.isValidAt(encoded, 4));
        assertFalse(LightingRetentionVertexData.isInvalidOnly(encoded));
    }

    @Test
    public void encodesVerticesOutsideBlockModelQuadsAsInvalid() {
        LightingRetentionVertexData data = new LightingRetentionVertexData();
        data.append(LightingRetentionVertexEncoding.consumeVertex());

        byte[] encoded = data.takeVertices(1);
        assertArrayEquals(new byte[] {0, 0, 0, 0}, encoded);
        assertTrue(LightingRetentionVertexData.isInvalidOnly(encoded));
        assertFalse(LightingRetentionVertexData.isValidAt(encoded, 0));
    }

    @Test
    public void acceptsACompleteRendererLightingSidecar() {
        LightingRetentionVertexData lighting = new LightingRetentionVertexData();
        for (int vertex = 0; vertex < 4; vertex++) {
            lighting.append(LightingRetentionVertexEncoding.Sample.valid(0.75F));
        }

        LightingRetentionVertexData.requireCompatibleVertexCount(
            lighting.takeVertices(4),
            4
        );
    }

    @Test
    public void rejectsAStreamWithTheWrongVertexCount() {
        LightingRetentionVertexData lighting = new LightingRetentionVertexData();
        lighting.append(LightingRetentionVertexEncoding.Sample.invalid());

        expectIllegalState(
            () -> LightingRetentionVertexData.requireCompatibleVertexCount(
                lighting.takeVertices(1),
                2
            ),
            "different vertex count"
        );
    }

    @Test
    public void restoresNestedQuadStateAndRejectsIncompleteEmission() {
        LightingRetentionVertexEncoding.runEnabledQuad(
            new float[] {0.1F, 0.2F, 0.3F, 0.4F},
            () -> {
                assertTrue(LightingRetentionVertexEncoding.isActive());
                for (int vertex = 0; vertex < 4; vertex++) {
                    LightingRetentionVertexEncoding.consumeVertex();
                }
            }
        );
        assertFalse(LightingRetentionVertexEncoding.isActive());

        expectIllegalState(
            () -> LightingRetentionVertexEncoding.runEnabledQuad(
                new float[] {0.1F, 0.2F, 0.3F, 0.4F},
                LightingRetentionVertexEncoding::consumeVertex
            ),
            "instead of four"
        );
        assertFalse(LightingRetentionVertexEncoding.isActive());
    }

    private static void expectIllegalState(Runnable runnable, String expectedMessage) {
        try {
            runnable.run();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains(expectedMessage));
        }
    }
}
