package io.github.minecraftaidiscovery.semantic.translucent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TranslucentProbeCandidatesTest {
    @Test
    public void emitsFrontToBackAndContributionRankedComparisons() {
        List<TranslucentProbeLayer> layers = Arrays.asList(
            layer(729, TranslucentProbeSource.FLUID, 32, 0.20F),
            layer(600, TranslucentProbeSource.BLOCK_MODEL, 255, 0.40F),
            emptyLayer(),
            emptyLayer()
        );

        TranslucentProbeCandidates result = TranslucentProbeCandidates.generate(
            layers,
            emptyLayer()
        );
        ByteBuffer front = littleEndian(result.getFrontToBack());
        assertEquals(729, Short.toUnsignedInt(front.getShort(0)));
        assertEquals(TranslucentProbeSource.FLUID.getCode(), Byte.toUnsignedInt(front.get(2)));
        assertEquals(32, Byte.toUnsignedInt(front.get(3)));
        assertEquals(0.20F, front.getFloat(4), 0.0F);
        assertEquals(600, Short.toUnsignedInt(front.getShort(8)));

        ByteBuffer ranked = littleEndian(result.getContributionRanked());
        assertEquals(600, Short.toUnsignedInt(ranked.getShort(0)));
        assertEquals(1.0F - 32.0F / 255.0F, ranked.getFloat(4), 0.000001F);
        assertEquals(729, Short.toUnsignedInt(ranked.getShort(8)));
        assertEquals(32.0F / 255.0F, ranked.getFloat(12), 0.000001F);
        assertEquals(0, result.getOverflow()[0]);
    }

    @Test
    public void preservesClassIdsAbove255AndReportsStorageOverflow() {
        List<TranslucentProbeLayer> layers = Arrays.asList(
            layer(443, TranslucentProbeSource.BLOCK_MODEL, 180, 0.10F),
            layer(444, TranslucentProbeSource.BLOCK_MODEL, 180, 0.20F),
            layer(662, TranslucentProbeSource.BLOCK_MODEL, 180, 0.30F),
            layer(729, TranslucentProbeSource.FLUID, 180, 0.40F)
        );

        TranslucentProbeCandidates result = TranslucentProbeCandidates.generate(
            layers,
            layer(700, TranslucentProbeSource.BLOCK_MODEL, 180, 0.50F)
        );

        ByteBuffer front = littleEndian(result.getFrontToBack());
        assertEquals(443, Short.toUnsignedInt(front.getShort(0)));
        assertEquals(729, Short.toUnsignedInt(front.getShort(24)));
        assertEquals(1, result.getOverflow()[0]);
        assertEquals(1, result.getOverflowPixelCount());
        assertEquals(32, result.getFrontToBack().length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void failsWhenAPeelAppearsAfterAnEmptySlot() {
        TranslucentProbeCandidates.generate(
            Arrays.asList(
                emptyLayer(),
                layer(600, TranslucentProbeSource.BLOCK_MODEL, 180, 0.2F),
                emptyLayer(),
                emptyLayer()
            ),
            emptyLayer()
        );
    }

    @Test
    public void calculatesExactCandidateBufferDimensions() {
        assertEquals(
            640 * 360 * 4 * 8,
            TranslucentProbeCandidates.expectedCandidateBufferLength(640, 360)
        );
        assertTrue(TranslucentProbeCandidates.FRONT_TO_BACK_ENCODING.contains("float32le"));
    }

    private static TranslucentProbeLayer layer(
        int classId,
        TranslucentProbeSource source,
        int alpha,
        float depth
    ) {
        return TranslucentProbeLayer.createForTesting(
            1,
            1,
            new int[] {classId},
            new byte[] {(byte)source.getCode()},
            new byte[] {(byte)alpha},
            new float[] {depth}
        );
    }

    private static TranslucentProbeLayer emptyLayer() {
        return layer(0, TranslucentProbeSource.NONE, 0, 1.0F);
    }

    private static ByteBuffer littleEndian(byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }
}
