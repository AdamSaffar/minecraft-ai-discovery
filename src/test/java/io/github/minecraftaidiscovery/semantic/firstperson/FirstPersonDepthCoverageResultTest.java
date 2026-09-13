package io.github.minecraftaidiscovery.semantic.firstperson;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FirstPersonDepthCoverageResultTest {
    @Test
    public void noFirstPersonStageHasNoCoverageAndNoSample() {
        FirstPersonDepthCoverageResult result =
            FirstPersonDepthCoverageResult.noFirstPersonStage(2, 2);

        assertArrayEquals(new byte[4], result.copyFullScreenCoverage());
        assertFalse(result.wasPostHandDepthSampled());
    }

    @Test
    public void sampledResultCopiesItsInput() {
        byte[] coverage = {0, (byte)255};
        FirstPersonDepthCoverageResult result = new FirstPersonDepthCoverageResult(
            2,
            1,
            coverage,
            true
        );
        coverage[1] = 0;

        assertArrayEquals(new byte[] {0, (byte)255}, result.copyFullScreenCoverage());
        assertTrue(result.wasPostHandDepthSampled());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCoverageWithoutASample() {
        new FirstPersonDepthCoverageResult(
            1,
            1,
            new byte[] {(byte)255},
            false
        );
    }
}
