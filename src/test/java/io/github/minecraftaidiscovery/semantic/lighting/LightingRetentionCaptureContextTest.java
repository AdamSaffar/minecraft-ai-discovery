package io.github.minecraftaidiscovery.semantic.lighting;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LightingRetentionCaptureContextTest {
    @Test
    public void labelsOnlyTheOrdinaryGammaRangeWithoutRejectingCompatibilityValues() {
        assertTrue(LightingRetentionCaptureContext.createForTesting(0.0).isOrdinaryGammaRange());
        assertTrue(LightingRetentionCaptureContext.createForTesting(1.0).isOrdinaryGammaRange());
        assertFalse(LightingRetentionCaptureContext.createForTesting(5.0).isOrdinaryGammaRange());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonFiniteGammaMetadata() {
        LightingRetentionCaptureContext.createForTesting(Double.NaN);
    }
}
