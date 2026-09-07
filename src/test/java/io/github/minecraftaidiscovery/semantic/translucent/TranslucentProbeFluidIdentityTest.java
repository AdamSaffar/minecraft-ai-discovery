package io.github.minecraftaidiscovery.semantic.translucent;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TranslucentProbeFluidIdentityTest {
    @Test
    public void stillAndFlowingWaterShareTheWaterIdentity() {
        Object water = new Object();
        Object flowingWater = new Object();

        assertEquals(
            TranslucentProbeFluidIdentity.WATER,
            TranslucentProbeFluidIdentity.classifyIdentity(
                water,
                water,
                flowingWater,
                new Object(),
                new Object()
            )
        );
        assertEquals(
            TranslucentProbeFluidIdentity.WATER,
            TranslucentProbeFluidIdentity.classifyIdentity(
                flowingWater,
                water,
                flowingWater,
                new Object(),
                new Object()
            )
        );
    }

    @Test
    public void stillAndFlowingLavaShareTheLavaIdentity() {
        Object lava = new Object();
        Object flowingLava = new Object();

        assertEquals(
            TranslucentProbeFluidIdentity.LAVA,
            TranslucentProbeFluidIdentity.classifyIdentity(
                lava,
                new Object(),
                new Object(),
                lava,
                flowingLava
            )
        );
        assertEquals(
            TranslucentProbeFluidIdentity.LAVA,
            TranslucentProbeFluidIdentity.classifyIdentity(
                flowingLava,
                new Object(),
                new Object(),
                lava,
                flowingLava
            )
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownFluidIdentity() {
        TranslucentProbeFluidIdentity.classifyIdentity(
            new Object(),
            new Object(),
            new Object(),
            new Object(),
            new Object()
        );
    }
}
