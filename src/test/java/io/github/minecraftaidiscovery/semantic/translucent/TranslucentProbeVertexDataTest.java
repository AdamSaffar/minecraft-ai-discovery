package io.github.minecraftaidiscovery.semantic.translucent;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TranslucentProbeVertexDataTest {
    @Test
    public void preservesUnsigned16ClassIdsAndSourceKinds() {
        TranslucentProbeVertexData data = new TranslucentProbeVertexData();
        appendQuad(data, 444, TranslucentProbeSource.BLOCK_MODEL);
        appendQuad(data, 65535, TranslucentProbeSource.FLUID);

        byte[] encoded = data.takeVertices(8);
        assertEquals(444, TranslucentProbeVertexData.classIdAt(encoded, 0));
        assertEquals(
            TranslucentProbeSource.BLOCK_MODEL,
            TranslucentProbeVertexData.sourceAt(encoded, 0)
        );
        assertEquals(65535, TranslucentProbeVertexData.classIdAt(encoded, 16));
        assertEquals(TranslucentProbeSource.FLUID, TranslucentProbeVertexData.sourceAt(encoded, 16));
        assertFalse(TranslucentProbeVertexData.isBackgroundOnly(encoded));
        assertNull(data.takeVertices(0));
    }

    @Test
    public void keepsWaterloggedBlockAndFluidContributionsSeparate() {
        TranslucentProbeVertexData data = new TranslucentProbeVertexData();
        appendQuad(data, 440, TranslucentProbeSource.BLOCK_MODEL);
        appendQuad(data, 729, TranslucentProbeSource.FLUID);

        byte[] encoded = data.takeVertices(8);
        assertEquals(440, TranslucentProbeVertexData.classIdAt(encoded, 0));
        assertEquals(
            TranslucentProbeSource.BLOCK_MODEL,
            TranslucentProbeVertexData.sourceAt(encoded, 0)
        );
        assertEquals(729, TranslucentProbeVertexData.classIdAt(encoded, 16));
        assertEquals(TranslucentProbeSource.FLUID, TranslucentProbeVertexData.sourceAt(encoded, 16));
    }

    @Test
    public void mirrorsVanillaQuadPermutation() {
        TranslucentProbeVertexData data = new TranslucentProbeVertexData();
        appendQuad(data, 100, TranslucentProbeSource.BLOCK_MODEL);
        appendQuad(data, 200, TranslucentProbeSource.FLUID);
        appendQuad(data, 300, TranslucentProbeSource.BLOCK_MODEL);

        data.reorderLastQuads(new int[] {2, 0, 1});
        byte[] encoded = data.takeVertices(12);

        assertEquals(300, TranslucentProbeVertexData.classIdAt(encoded, 0));
        assertEquals(100, TranslucentProbeVertexData.classIdAt(encoded, 16));
        assertEquals(200, TranslucentProbeVertexData.classIdAt(encoded, 32));
        TranslucentProbeVertexData.requireConstantQuadContributions(encoded);
    }

    @Test
    public void snapshotSurvivesASecondResort() {
        TranslucentProbeVertexData original = new TranslucentProbeVertexData();
        appendQuad(original, 143, TranslucentProbeSource.BLOCK_MODEL);
        appendQuad(original, 729, TranslucentProbeSource.FLUID);
        byte[] snapshot = original.snapshotLastVertices(8);

        TranslucentProbeVertexData restored = new TranslucentProbeVertexData();
        restored.restoreSnapshot(snapshot);
        restored.reorderLastQuads(new int[] {1, 0});
        byte[] encoded = restored.takeVertices(8);

        assertEquals(729, TranslucentProbeVertexData.classIdAt(encoded, 0));
        assertEquals(143, TranslucentProbeVertexData.classIdAt(encoded, 16));
        assertArrayEquals(snapshot, original.takeVertices(8));
    }

    @Test
    public void recognizesOnlyTheClassAndSourceSentinelAsBackground() {
        TranslucentProbeVertexData data = new TranslucentProbeVertexData();
        appendQuad(data, 0, TranslucentProbeSource.NONE);

        assertTrue(TranslucentProbeVertexData.isBackgroundOnly(data.takeVertices(4)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicatePermutationEntries() {
        TranslucentProbeVertexData data = new TranslucentProbeVertexData();
        appendQuad(data, 143, TranslucentProbeSource.BLOCK_MODEL);
        appendQuad(data, 729, TranslucentProbeSource.FLUID);
        data.reorderLastQuads(new int[] {0, 0});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBackgroundWithAVisibleSource() {
        new TranslucentProbeVertexData().append(0, TranslucentProbeSource.FLUID);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsMissingVertices() {
        TranslucentProbeVertexData data = new TranslucentProbeVertexData();
        appendQuad(data, 143, TranslucentProbeSource.BLOCK_MODEL);
        data.takeVertices(8);
    }

    private static void appendQuad(
        TranslucentProbeVertexData data,
        int classId,
        TranslucentProbeSource source
    ) {
        for (int vertex = 0; vertex < 4; vertex++) {
            data.append(classId, source);
        }
    }
}
