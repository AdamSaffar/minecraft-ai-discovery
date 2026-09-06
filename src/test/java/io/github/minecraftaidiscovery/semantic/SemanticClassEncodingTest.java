package io.github.minecraftaidiscovery.semantic;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class SemanticClassEncodingTest {
    @Test
    public void roundTripsEntireRequiredUnsigned16BoundarySet() {
        int[] classIds = {0, 1, 255, 256, 763, 65535};

        byte[] encoded = SemanticClassEncoding.encodeU16Le(classIds.length, 1, classIds);

        assertEquals(classIds.length * 2, encoded.length);
        assertArrayEquals(classIds, SemanticClassEncoding.decodeU16Le(classIds.length, 1, encoded));
    }

    @Test
    public void writesExactLittleEndianBytesWithoutTruncatingHighIds() {
        byte[] encoded = SemanticClassEncoding.encodeU16Le(3, 1, new int[] {255, 256, 763});

        assertArrayEquals(
            new byte[] {(byte)0xFF, 0x00, 0x00, 0x01, (byte)0xFB, 0x02},
            encoded
        );
        assertEquals(256, SemanticClassEncoding.decodeGpuBytes(0, 1));
        assertEquals(763, SemanticClassEncoding.decodeGpuBytes(0xFB, 0x02));
    }

    @Test
    public void validatesDimensionsLengthAndUnsignedRange() {
        assertEquals(24, SemanticClassEncoding.expectedBufferLength(4, 3));
        expectIllegalArgument(
            () -> SemanticClassEncoding.encodeU16Le(2, 1, new int[] {1}),
            "Expected 2 semantic class IDs"
        );
        expectIllegalArgument(
            () -> SemanticClassEncoding.decodeU16Le(2, 1, new byte[3]),
            "Expected 4 class-buffer bytes"
        );
        expectIllegalArgument(
            () -> SemanticClassEncoding.encodeU16Le(1, 1, new int[] {65536}),
            "outside the uint16 range"
        );
    }

    @Test
    public void assignsDeterministicNonStrategicFalseColorsAndKeepsBackgroundBlack() {
        assertEquals(0, SemanticClassEncoding.falseColorRgb(0));
        assertEquals(
            SemanticClassEncoding.falseColorRgb(444),
            SemanticClassEncoding.falseColorRgb(444)
        );
        assertNotEquals(0, SemanticClassEncoding.falseColorRgb(444));
        assertNotEquals(
            SemanticClassEncoding.falseColorRgb(139),
            SemanticClassEncoding.falseColorRgb(444)
        );
    }

    private static void expectIllegalArgument(ThrowingRunnable runnable, String expectedMessage) {
        try {
            runnable.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains(expectedMessage));
        }
    }

    private interface ThrowingRunnable {
        void run();
    }
}
