package io.github.minecraftaidiscovery.semantic.terrain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TerrainObservationSequenceValidatorTest {
    private static final TerrainObservationContract CONTRACT =
        new TerrainObservationContract(
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            1
        );

    @Test
    public void acceptsStrictlyIncreasingValidAndInvalidFrames() {
        TerrainObservationSequenceValidator validator =
            new TerrainObservationSequenceValidator();

        validator.accept(TerrainObservationFrame.invalid(5));
        validator.accept(
            TerrainObservationFrame.valid(
                CONTRACT,
                8,
                new byte[TerrainObservationContract.PIXEL_COUNT],
                new byte[TerrainObservationContract.PIXEL_COUNT],
                new short[TerrainObservationContract.PIXEL_COUNT],
                new short[TerrainObservationContract.expectedForegroundLength()],
                new byte[TerrainObservationContract.PIXEL_COUNT]
            )
        );

        assertTrue(validator.isInitialized());
        assertEquals(8, validator.getPreviousSequence());
    }

    @Test
    public void comparesSequenceNumbersAsUnsignedValues() {
        TerrainObservationSequenceValidator validator =
            new TerrainObservationSequenceValidator();

        validator.accept(TerrainObservationFrame.invalid(Long.MAX_VALUE));
        validator.accept(TerrainObservationFrame.invalid(Long.MIN_VALUE));

        assertEquals(Long.MIN_VALUE, validator.getPreviousSequence());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateSequence() {
        TerrainObservationSequenceValidator validator =
            new TerrainObservationSequenceValidator();

        validator.accept(TerrainObservationFrame.invalid(5));
        validator.accept(TerrainObservationFrame.invalid(5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDecreasingSequence() {
        TerrainObservationSequenceValidator validator =
            new TerrainObservationSequenceValidator();

        validator.accept(TerrainObservationFrame.invalid(9));
        validator.accept(TerrainObservationFrame.invalid(8));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsReadingSequenceBeforeAFrameIsAccepted() {
        new TerrainObservationSequenceValidator().getPreviousSequence();
    }
}
