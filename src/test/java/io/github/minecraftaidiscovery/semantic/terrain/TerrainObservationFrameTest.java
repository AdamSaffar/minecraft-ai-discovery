package io.github.minecraftaidiscovery.semantic.terrain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TerrainObservationFrameTest {
    private static final TerrainObservationContract CONTRACT =
        new TerrainObservationContract(
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            0xFFFF
        );

    @Test
    public void acceptsAnAllBackgroundFrame() {
        TerrainObservationFrame frame = validFrame(
            new byte[TerrainObservationContract.PIXEL_COUNT],
            new byte[TerrainObservationContract.PIXEL_COUNT],
            new short[TerrainObservationContract.PIXEL_COUNT],
            new short[TerrainObservationContract.expectedForegroundLength()],
            new byte[TerrainObservationContract.PIXEL_COUNT]
        );

        assertTrue(frame.isValid());
        assertEquals(TerrainObservationFrame.FrameStatus.VALID, frame.getFrameStatus());
    }

    @Test
    public void acceptsThreeFrontToBackIdentitiesAndOverflow() {
        byte[] baseState = backgroundStates();
        short[] foreground = foregroundIdentities();
        byte[] overflow = overflow();
        baseState[0] = (byte)TerrainObservationContract.BASE_UNKNOWN;
        foreground[0] = 1;
        foreground[1] = 2;
        foreground[2] = (short)0xFFFF;
        overflow[0] = 1;

        TerrainObservationFrame frame = validFrame(
            clearOcclusion(),
            baseState,
            baseIdentities(),
            foreground,
            overflow
        );

        assertEquals((short)0xFFFF, frame.copyForegroundIdentity()[2]);
        assertEquals(1, frame.copyForegroundOverflow()[0]);
    }

    @Test
    public void preservesKnownForegroundAboveAnUnknownBase() {
        byte[] baseState = backgroundStates();
        short[] foreground = foregroundIdentities();
        baseState[7] = (byte)TerrainObservationContract.BASE_UNKNOWN;
        foreground[7 * TerrainObservationContract.FOREGROUND_CAPACITY] = 23;

        TerrainObservationFrame frame = validFrame(
            clearOcclusion(),
            baseState,
            baseIdentities(),
            foreground,
            overflow()
        );

        assertEquals(
            TerrainObservationContract.BASE_UNKNOWN,
            Byte.toUnsignedInt(frame.copyBaseState()[7])
        );
        assertEquals(
            23,
            Short.toUnsignedInt(
                frame.copyForegroundIdentity()[
                    7 * TerrainObservationContract.FOREGROUND_CAPACITY
                ]
            )
        );
    }

    @Test
    public void acceptsAWholePixelUnknownWithNoTerrainPayload() {
        byte[] occlusion = clearOcclusion();
        byte[] baseState = backgroundStates();
        occlusion[9] = (byte)TerrainObservationContract.OCCLUSION_UNKNOWN;
        baseState[9] = (byte)TerrainObservationContract.BASE_UNKNOWN;

        validFrame(
            occlusion,
            baseState,
            baseIdentities(),
            foregroundIdentities(),
            overflow()
        );
    }

    @Test
    public void copiesMutablePayloadArrays() {
        byte[] baseState = backgroundStates();
        short[] baseIdentity = baseIdentities();
        baseState[0] = (byte)TerrainObservationContract.BASE_IDENTITY;
        baseIdentity[0] = 31;
        TerrainObservationFrame frame = validFrame(
            clearOcclusion(),
            baseState,
            baseIdentity,
            foregroundIdentities(),
            overflow()
        );

        baseState[0] = 0;
        baseIdentity[0] = 0;
        short[] returned = frame.copyBaseIdentity();
        returned[0] = 0;

        assertEquals(
            TerrainObservationContract.BASE_IDENTITY,
            Byte.toUnsignedInt(frame.copyBaseState()[0])
        );
        assertEquals(31, Short.toUnsignedInt(frame.copyBaseIdentity()[0]));
    }

    @Test
    public void invalidFrameHasNoPayload() {
        TerrainObservationFrame frame = TerrainObservationFrame.invalid(4);

        assertFalse(frame.isValid());
        assertEquals(
            TerrainObservationFrame.FrameStatus.INVALID_FRAME,
            frame.getFrameStatus()
        );
        assertArrayEquals(new byte[0], frame.copyOcclusionState());
        assertArrayEquals(new short[0], frame.copyBaseIdentity());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongPayloadLength() {
        TerrainObservationFrame.valid(
            CONTRACT,
            1,
            new byte[1],
            backgroundStates(),
            baseIdentities(),
            foregroundIdentities(),
            overflow()
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownStateCode() {
        byte[] occlusion = clearOcclusion();
        occlusion[0] = 2;
        validFrame(
            occlusion,
            backgroundStates(),
            baseIdentities(),
            foregroundIdentities(),
            overflow()
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingIdentityForIdentityState() {
        byte[] baseState = backgroundStates();
        baseState[0] = (byte)TerrainObservationContract.BASE_IDENTITY;
        validFrame(
            clearOcclusion(),
            baseState,
            baseIdentities(),
            foregroundIdentities(),
            overflow()
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsIdentityOutsideIdentityState() {
        short[] baseIdentity = baseIdentities();
        baseIdentity[0] = 5;
        validFrame(
            clearOcclusion(),
            backgroundStates(),
            baseIdentity,
            foregroundIdentities(),
            overflow()
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsIdentityOutsideThePinnedManifest() {
        TerrainObservationContract smallContract = new TerrainObservationContract(
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            4
        );
        byte[] baseState = backgroundStates();
        short[] baseIdentity = baseIdentities();
        baseState[0] = (byte)TerrainObservationContract.BASE_IDENTITY;
        baseIdentity[0] = 5;

        TerrainObservationFrame.valid(
            smallContract,
            3,
            clearOcclusion(),
            baseState,
            baseIdentity,
            foregroundIdentities(),
            overflow()
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAGapBetweenForegroundSlots() {
        short[] foreground = foregroundIdentities();
        foreground[1] = 8;
        validFrame(
            clearOcclusion(),
            backgroundStates(),
            baseIdentities(),
            foreground,
            overflow()
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverflowWithoutThreeSlots() {
        byte[] baseState = backgroundStates();
        byte[] overflow = overflow();
        baseState[0] = (byte)TerrainObservationContract.BASE_UNKNOWN;
        overflow[0] = 1;
        validFrame(
            clearOcclusion(),
            baseState,
            baseIdentities(),
            foregroundIdentities(),
            overflow
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverflowWithAKnownBase() {
        byte[] baseState = backgroundStates();
        short[] baseIdentity = baseIdentities();
        short[] foreground = foregroundIdentities();
        byte[] overflow = overflow();
        baseState[0] = (byte)TerrainObservationContract.BASE_IDENTITY;
        baseIdentity[0] = 4;
        foreground[0] = 1;
        foreground[1] = 2;
        foreground[2] = 3;
        overflow[0] = 1;
        validFrame(
            clearOcclusion(),
            baseState,
            baseIdentity,
            foreground,
            overflow
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTerrainDataUnderWholePixelUnknown() {
        byte[] occlusion = clearOcclusion();
        byte[] baseState = backgroundStates();
        short[] foreground = foregroundIdentities();
        occlusion[0] = (byte)TerrainObservationContract.OCCLUSION_UNKNOWN;
        baseState[0] = (byte)TerrainObservationContract.BASE_UNKNOWN;
        foreground[0] = 1;
        validFrame(
            occlusion,
            baseState,
            baseIdentities(),
            foreground,
            overflow()
        );
    }

    private static TerrainObservationFrame validFrame(
        byte[] occlusion,
        byte[] baseState,
        short[] baseIdentity,
        short[] foreground,
        byte[] overflow
    ) {
        return TerrainObservationFrame.valid(
            CONTRACT,
            3,
            occlusion,
            baseState,
            baseIdentity,
            foreground,
            overflow
        );
    }

    private static byte[] clearOcclusion() {
        return new byte[TerrainObservationContract.PIXEL_COUNT];
    }

    private static byte[] backgroundStates() {
        return new byte[TerrainObservationContract.PIXEL_COUNT];
    }

    private static short[] baseIdentities() {
        return new short[TerrainObservationContract.PIXEL_COUNT];
    }

    private static short[] foregroundIdentities() {
        return new short[TerrainObservationContract.expectedForegroundLength()];
    }

    private static byte[] overflow() {
        return new byte[TerrainObservationContract.PIXEL_COUNT];
    }
}
