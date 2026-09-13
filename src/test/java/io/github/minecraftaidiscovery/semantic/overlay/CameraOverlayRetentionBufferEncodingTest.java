package io.github.minecraftaidiscovery.semantic.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CameraOverlayRetentionBufferEncodingTest {
    @Test
    public void masksBackgroundWithoutChangingPositiveBaseValues() {
        byte[] retention = {(byte)255, (byte)204, (byte)96, 0};
        int[] baseClasses = {0, 4, 0, 7};

        assertArrayEquals(
            new byte[] {0, (byte)204, 0, 0},
            CameraOverlayRetentionBufferEncoding.maskToPositiveBase(
                2,
                2,
                retention,
                baseClasses
            )
        );
    }

    @Test
    public void composesRepeatedSourceAlphaRetention() {
        int once = CameraOverlayRetentionBufferEncoding.composeRetentionU8(255, 128);
        int twice = CameraOverlayRetentionBufferEncoding.composeRetentionU8(once, 128);

        assertEquals(127, once);
        assertEquals(63, twice);
        assertEquals(255, CameraOverlayRetentionBufferEncoding.composeRetentionU8(255, 0));
        assertEquals(0, CameraOverlayRetentionBufferEncoding.composeRetentionU8(255, 255));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongBufferLength() {
        CameraOverlayRetentionBufferEncoding.requireU8(2, 2, new byte[3]);
    }

    @Test(expected = ArithmeticException.class)
    public void rejectsDimensionOverflow() {
        CameraOverlayRetentionBufferEncoding.requireDimensions(Integer.MAX_VALUE, 2);
    }
}
