package io.github.minecraftaidiscovery.semantic.firstperson;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FirstPersonDepthCoverageEncodingTest {
    @Test
    public void encodesDepthWritesAndFlipsOpenGlRows() {
        float[] bottomToTopDepth = {
            1.0F, 0.25F,
            0.0F, 1.0F
        };

        assertArrayEquals(
            new byte[] {(byte)255, 0, 0, (byte)255},
            FirstPersonDepthCoverageEncoding.encodeBottomToTopDepth(
                2,
                2,
                bottomToTopDepth
            )
        );
    }

    @Test
    public void masksCoverageToPositiveBasePixels() {
        byte[] coverage = {(byte)255, (byte)255, 0, (byte)255};
        int[] baseClasses = {0, 4, 0, 7};

        assertArrayEquals(
            new byte[] {0, (byte)255, 0, (byte)255},
            FirstPersonDepthCoverageEncoding.maskToPositiveBase(
                2,
                2,
                coverage,
                baseClasses
            )
        );
    }

    @Test
    public void countsBinaryCoverage() {
        assertEquals(
            2,
            FirstPersonDepthCoverageEncoding.countCovered(
                new byte[] {0, (byte)255, (byte)255, 0}
            )
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonBinaryCoverage() {
        FirstPersonDepthCoverageEncoding.requireBinary(1, 1, new byte[] {1});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonFiniteDepth() {
        FirstPersonDepthCoverageEncoding.encodeBottomToTopDepth(
            1,
            1,
            new float[] {Float.NaN}
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDepthOutsideWindowRange() {
        FirstPersonDepthCoverageEncoding.encodeBottomToTopDepth(
            1,
            1,
            new float[] {1.01F}
        );
    }
}
