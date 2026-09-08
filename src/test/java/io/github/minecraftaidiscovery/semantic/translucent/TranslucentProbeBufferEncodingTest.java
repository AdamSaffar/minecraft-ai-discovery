package io.github.minecraftaidiscovery.semantic.translucent;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;

public class TranslucentProbeBufferEncodingTest {
    @Test
    public void writesFloatDepthInExactLittleEndianOrder() {
        byte[] encoded = TranslucentProbeBufferEncoding.encodeFloat32Le(
            2,
            1,
            new float[] {0.25F, 1.0F}
        );

        assertEquals(8, encoded.length);
        assertArrayEquals(
            ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(0.25F)
                .putFloat(1.0F)
                .array(),
            encoded
        );
    }

    @Test
    public void serializesLayerRecordsWithoutTruncatingClassIds() {
        TranslucentProbeLayer layer = TranslucentProbeLayer.createForTesting(
            1,
            1,
            new int[] {662},
            new byte[] {(byte)TranslucentProbeSource.BLOCK_MODEL.getCode()},
            new byte[] {(byte)190},
            new float[] {0.75F}
        );

        ByteBuffer encoded = ByteBuffer.wrap(TranslucentProbeBufferEncoding.encodeLayer(layer))
            .order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(8, encoded.capacity());
        assertEquals(662, Short.toUnsignedInt(encoded.getShort()));
        assertEquals(TranslucentProbeSource.BLOCK_MODEL.getCode(), Byte.toUnsignedInt(encoded.get()));
        assertEquals(190, Byte.toUnsignedInt(encoded.get()));
        assertEquals(0.75F, encoded.getFloat(), 0.0F);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMismatchedDimensions() {
        TranslucentProbeBufferEncoding.encodeFloat32Le(2, 1, new float[] {0.5F});
    }
}
