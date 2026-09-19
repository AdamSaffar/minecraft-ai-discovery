package io.github.minecraftaidiscovery.semantic.terrain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TerrainObservationContractTest {
    @Test
    public void freezesTheApprovedV1ShapeAndCapacity() {
        assertEquals(640, TerrainObservationContract.WIDTH);
        assertEquals(360, TerrainObservationContract.HEIGHT);
        assertEquals(230400, TerrainObservationContract.PIXEL_COUNT);
        assertEquals(3, TerrainObservationContract.FOREGROUND_CAPACITY);
        assertEquals(691200, TerrainObservationContract.expectedForegroundLength());
        assertEquals(0, TerrainObservationFrame.FrameStatus.VALID.getCode());
        assertEquals(1, TerrainObservationFrame.FrameStatus.INVALID_FRAME.getCode());
    }

    @Test
    public void normalizesTheManifestFingerprint() {
        TerrainObservationContract contract = new TerrainObservationContract(
            "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789",
            752
        );

        assertEquals(
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            contract.getTerrainManifestSha256()
        );
        assertEquals(752, contract.getTerrainManifestEntryCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnInvalidManifestFingerprint() {
        new TerrainObservationContract("not-a-sha256", 752);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAnInvalidManifestEntryCount() {
        new TerrainObservationContract(
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            0
        );
    }
}
