package io.github.minecraftaidiscovery.semantic.translucent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import org.junit.Test;

public class TranslucentProbeTerrainLayerTest {
    @Test
    public void recognizesOnlyTheFourProbeTerrainLayers() {
        Object solid = new Object();
        Object cutoutMipped = new Object();
        Object cutout = new Object();
        Object translucent = new Object();

        assertEquals(
            TranslucentProbeTerrainLayer.SOLID,
            classify(solid, solid, cutoutMipped, cutout, translucent)
        );
        assertEquals(
            TranslucentProbeTerrainLayer.CUTOUT_MIPPED,
            classify(cutoutMipped, solid, cutoutMipped, cutout, translucent)
        );
        assertEquals(
            TranslucentProbeTerrainLayer.CUTOUT,
            classify(cutout, solid, cutoutMipped, cutout, translucent)
        );
        assertEquals(
            TranslucentProbeTerrainLayer.TRANSLUCENT,
            classify(translucent, solid, cutoutMipped, cutout, translucent)
        );
        assertNull(classify(new Object(), solid, cutoutMipped, cutout, translucent));
    }

    @Test
    public void keepsVanillaTerrainLayerOrder() {
        assertEquals(
            Arrays.asList(
                TranslucentProbeTerrainLayer.SOLID,
                TranslucentProbeTerrainLayer.CUTOUT_MIPPED,
                TranslucentProbeTerrainLayer.CUTOUT,
                TranslucentProbeTerrainLayer.TRANSLUCENT
            ),
            TranslucentProbeTerrainLayer.supportedInOrder()
        );
    }

    private static TranslucentProbeTerrainLayer classify(
        Object layer,
        Object solid,
        Object cutoutMipped,
        Object cutout,
        Object translucent
    ) {
        return TranslucentProbeMinecraftRenderLayers.classifyIdentity(
            layer,
            solid,
            cutoutMipped,
            cutout,
            translucent
        );
    }
}
