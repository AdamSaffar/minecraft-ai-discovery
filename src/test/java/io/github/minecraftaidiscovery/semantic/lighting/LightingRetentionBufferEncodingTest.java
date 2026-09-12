package io.github.minecraftaidiscovery.semantic.lighting;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LightingRetentionBufferEncodingTest {
    @Test
    public void enforcesRgbDimensionsAndBuildsAnExplicitBaseValidityMask() {
        assertEquals(640 * 360 * 3, LightingRetentionBufferEncoding.expectedRgbLength(640, 360));

        byte[] rgb = new byte[2 * 2 * 3];
        assertEquals(rgb, LightingRetentionBufferEncoding.requireRgb(2, 2, rgb));
        assertArrayEquals(
            new byte[] {0, (byte)0xFF, 0, 0},
            LightingRetentionBufferEncoding.validityForSupportedBase(
                2,
                2,
                new int[] {0, 1, 662, 0},
                new byte[] {0, (byte)0xFF, 0, 0}
            )
        );
    }

    @Test
    public void reconstructsTheQuantizedRendererProductDeterministically() {
        assertEquals(
            (127.0F / 255.0F) * (200.0F / 255.0F),
            LightingRetentionBufferEncoding.reconstructNormalizedResponse(127, 200),
            0.0F
        );
        assertEquals(0.0F, LightingRetentionBufferEncoding.reconstructNormalizedResponse(0, 255), 0.0F);
        assertEquals(1.0F, LightingRetentionBufferEncoding.reconstructNormalizedResponse(255, 255), 0.0F);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongRgbLength() {
        LightingRetentionBufferEncoding.requireRgb(640, 360, new byte[640 * 360]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfRangeReconstructionInput() {
        LightingRetentionBufferEncoding.reconstructNormalizedResponse(256, 10);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsRendererLightingWithoutAWinningBaseFragment() {
        LightingRetentionBufferEncoding.validityForSupportedBase(
            1,
            1,
            new int[] {0},
            new byte[] {(byte)0xFF}
        );
    }
}
