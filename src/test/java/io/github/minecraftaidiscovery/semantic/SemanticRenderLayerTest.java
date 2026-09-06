package io.github.minecraftaidiscovery.semantic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class SemanticRenderLayerTest {
    @Test
    public void keepsMinecraftTerrainOrderAndCoverageModes() {
        assertEquals(
            Arrays.asList(
                SemanticRenderLayer.SOLID,
                SemanticRenderLayer.CUTOUT_MIPPED,
                SemanticRenderLayer.CUTOUT
            ),
            SemanticRenderLayer.supportedInOrder()
        );
        assertFalse(SemanticRenderLayer.SOLID.usesTextureCoverage());
        assertFalse(SemanticRenderLayer.SOLID.usesMipmaps());
        assertTrue(SemanticRenderLayer.CUTOUT_MIPPED.usesTextureCoverage());
        assertTrue(SemanticRenderLayer.CUTOUT_MIPPED.usesMipmaps());
        assertTrue(SemanticRenderLayer.CUTOUT.usesTextureCoverage());
        assertFalse(SemanticRenderLayer.CUTOUT.usesMipmaps());
    }

    @Test
    public void classifiesOnlyRequestedLayerSingletons() {
        Object solid = new Object();
        Object cutoutMipped = new Object();
        Object cutout = new Object();

        assertEquals(
            SemanticRenderLayer.SOLID,
            SemanticMinecraftRenderLayers.classifyIdentity(solid, solid, cutoutMipped, cutout)
        );
        assertEquals(
            SemanticRenderLayer.CUTOUT_MIPPED,
            SemanticMinecraftRenderLayers.classifyIdentity(cutoutMipped, solid, cutoutMipped, cutout)
        );
        assertEquals(
            SemanticRenderLayer.CUTOUT,
            SemanticMinecraftRenderLayers.classifyIdentity(cutout, solid, cutoutMipped, cutout)
        );
        assertNull(
            SemanticMinecraftRenderLayers.classifyIdentity(
                new Object(),
                solid,
                cutoutMipped,
                cutout
            )
        );
    }
}
