package io.github.minecraftaidiscovery.semantic.translucent;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FogRetentionParametersTest {
    @Test
    public void evaluatesExp2RetentionFromFogDistance() {
        FogRetentionParameters parameters = FogRetentionParameters.createForTesting(
            FogRetentionParameters.Mode.EXP2,
            FogRetentionParameters.DistanceMode.NV_EYE_RADIAL,
            0.05F,
            0.0F,
            1.0F
        );

        assertEquals(1.0F, parameters.retention(0.0F), 0.000001F);
        assertEquals((float)Math.exp(-1.0F), parameters.retention(20.0F), 0.000001F);
    }

    @Test
    public void evaluatesAndClampsLinearRetention() {
        FogRetentionParameters parameters = FogRetentionParameters.createForTesting(
            FogRetentionParameters.Mode.LINEAR,
            FogRetentionParameters.DistanceMode.STANDARD_EYE_PLANE_ABSOLUTE,
            0.0F,
            24.0F,
            32.0F
        );

        assertEquals(1.0F, parameters.retention(20.0F), 0.0F);
        assertEquals(0.5F, parameters.retention(28.0F), 0.0F);
        assertEquals(0.0F, parameters.retention(40.0F), 0.0F);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsReversedLinearRange() {
        FogRetentionParameters.createForTesting(
            FogRetentionParameters.Mode.LINEAR,
            FogRetentionParameters.DistanceMode.NV_EYE_PLANE_ABSOLUTE,
            0.0F,
            32.0F,
            24.0F
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeFogDistance() {
        FogRetentionParameters parameters = FogRetentionParameters.createForTesting(
            FogRetentionParameters.Mode.EXP2,
            FogRetentionParameters.DistanceMode.STANDARD_EYE_PLANE_ABSOLUTE,
            0.05F,
            0.0F,
            1.0F
        );

        parameters.retention(-1.0F);
    }
}
