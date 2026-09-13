package io.github.minecraftaidiscovery.semantic.visibility;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BaseInfluenceCompositionTest {
    @Test
    public void composesSupportedFactorsAndMasksBackground() {
        BaseInfluenceCompositionResult result = BaseInfluenceComposition.compose(
            2,
            1,
            new int[] {7, 0},
            new byte[] {(byte)200, (byte)100, (byte)50, 0, 0, 0},
            new byte[] {(byte)255, 0},
            new byte[] {(byte)128, 0},
            new byte[] {(byte)64, (byte)200},
            new byte[] {0, 0},
            new byte[] {(byte)127, 0},
            new byte[] {0, 0}
        );

        assertEquals(2, result.getWidth());
        assertEquals(1, result.getHeight());
        assertArrayEquals(
            new byte[] {
                (byte)expectedProduct(200, 128, 191, 127),
                (byte)expectedProduct(100, 128, 191, 127),
                (byte)expectedProduct(50, 128, 191, 127),
                0,
                0,
                0
            },
            result.copyRgb()
        );
        assertArrayEquals(
            new byte[] {(byte)BaseInfluenceReasonEncoding.SUPPORTED, 0},
            result.copyReasons()
        );
    }

    @Test
    public void preservesZeroAndFullScaleEndpoints() {
        assertEquals(255, BaseInfluenceComposition.multiplyU8Factors(255, 255, 255, 255));
        assertEquals(128, BaseInfluenceComposition.multiplyU8Factors(255, 128, 255, 255));
        assertEquals(0, BaseInfluenceComposition.multiplyU8Factors(255, 255, 0, 255));
        assertEquals(0, BaseInfluenceComposition.multiplyU8Factors(0, 255, 255, 255));
    }

    @Test
    public void reportsEveryUnsupportedReasonWithoutProducingDarkMeasurements() {
        BaseInfluenceCompositionResult result = BaseInfluenceComposition.compose(
            4,
            1,
            new int[] {1, 2, 3, 4},
            filled(12, 255),
            new byte[] {0, (byte)255, (byte)255, 0},
            filled(4, 255),
            new byte[4],
            new byte[] {0, 1, 0, 1},
            filled(4, 255),
            new byte[] {0, 0, (byte)255, (byte)255}
        );

        assertArrayEquals(new byte[12], result.copyRgb());
        assertArrayEquals(
            new byte[] {
                (byte)BaseInfluenceReasonEncoding.UNSUPPORTED_LIGHTING,
                (byte)BaseInfluenceReasonEncoding.TRANSLUCENT_OVERFLOW,
                (byte)BaseInfluenceReasonEncoding.FIRST_PERSON_RETENTION_UNSUPPORTED,
                (byte)(BaseInfluenceReasonEncoding.UNSUPPORTED_LIGHTING
                    | BaseInfluenceReasonEncoding.TRANSLUCENT_OVERFLOW
                    | BaseInfluenceReasonEncoding.FIRST_PERSON_RETENTION_UNSUPPORTED)
            },
            result.copyReasons()
        );
    }

    @Test
    public void resultDoesNotExposeMutableBuffers() {
        BaseInfluenceCompositionResult result = BaseInfluenceComposition.compose(
            1,
            1,
            new int[] {1},
            filled(3, 255),
            filled(1, 255),
            filled(1, 255),
            new byte[1],
            new byte[1],
            filled(1, 255),
            new byte[1]
        );

        byte[] rgb = result.copyRgb();
        byte[] reasons = result.copyReasons();
        rgb[0] = 0;
        reasons[0] = 0;

        assertEquals(255, Byte.toUnsignedInt(result.copyRgb()[0]));
        assertEquals(
            BaseInfluenceReasonEncoding.SUPPORTED,
            Byte.toUnsignedInt(result.copyReasons()[0])
        );
    }

    @Test
    public void recognizesSupportedAndCombinedReasonCodes() {
        assertTrue(BaseInfluenceReasonEncoding.isSupported(1));
        assertFalse(BaseInfluenceReasonEncoding.isSupported(0));
        assertFalse(BaseInfluenceReasonEncoding.isSupported(2 | 8));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongBufferLength() {
        BaseInfluenceComposition.compose(
            1,
            1,
            new int[] {1},
            new byte[2],
            filled(1, 255),
            filled(1, 255),
            new byte[1],
            new byte[1],
            filled(1, 255),
            new byte[1]
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonBinaryLightingValidity() {
        composeWithSingleChangedInput(new byte[] {1}, new byte[1], new byte[1]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonBinaryOverflow() {
        composeWithSingleChangedInput(filled(1, 255), new byte[] {(byte)255}, new byte[1]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonBinaryFirstPersonCoverage() {
        composeWithSingleChangedInput(filled(1, 255), new byte[1], new byte[] {1});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBaseMaskedValuesOnBackground() {
        BaseInfluenceComposition.compose(
            1,
            1,
            new int[] {0},
            new byte[3],
            new byte[1],
            new byte[1],
            new byte[1],
            new byte[1],
            new byte[] {(byte)255},
            new byte[1]
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsClassOutsideUnsignedSixteenBitRange() {
        BaseInfluenceComposition.compose(
            1,
            1,
            new int[] {65536},
            filled(3, 255),
            filled(1, 255),
            filled(1, 255),
            new byte[1],
            new byte[1],
            filled(1, 255),
            new byte[1]
        );
    }

    private static void composeWithSingleChangedInput(
        byte[] lightingValidity,
        byte[] overflow,
        byte[] firstPersonCoverage
    ) {
        BaseInfluenceComposition.compose(
            1,
            1,
            new int[] {1},
            filled(3, 255),
            lightingValidity,
            filled(1, 255),
            new byte[1],
            overflow,
            filled(1, 255),
            firstPersonCoverage
        );
    }

    private static int expectedProduct(int first, int second, int third, int fourth) {
        long denominator = 255L * 255L * 255L;
        long numerator = (long)first * second * third * fourth;
        return (int)((numerator + denominator / 2L) / denominator);
    }

    private static byte[] filled(int length, int value) {
        byte[] values = new byte[length];
        for (int index = 0; index < length; index++) {
            values[index] = (byte)value;
        }
        return values;
    }
}
