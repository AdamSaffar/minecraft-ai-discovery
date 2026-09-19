package io.github.minecraftaidiscovery.semantic.terrain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TerrainObservationContract {
    public static final String CONTRACT_NAME =
        "semantic-assisted-terrain-observation-v1";
    public static final String MINECRAFT_VERSION = "1.16.1";
    public static final String UI_EXCLUSION_POLICY = "fixed-ui-exclusion-v1";
    public static final int WIDTH = 640;
    public static final int HEIGHT = 360;
    public static final int PIXEL_COUNT = WIDTH * HEIGHT;
    public static final int FOREGROUND_CAPACITY = 3;

    public static final int OCCLUSION_CLEAR = 0;
    public static final int OCCLUSION_UNKNOWN = 1;

    public static final int BASE_BACKGROUND = 0;
    public static final int BASE_IDENTITY = 1;
    public static final int BASE_UNKNOWN = 2;

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final String terrainManifestSha256;
    private final int terrainManifestEntryCount;

    public TerrainObservationContract(
        String terrainManifestSha256,
        int terrainManifestEntryCount
    ) {
        if (terrainManifestSha256 == null
            || !SHA256.matcher(terrainManifestSha256).matches()) {
            throw new IllegalArgumentException(
                "Terrain observation manifest fingerprint must be SHA-256"
            );
        }
        if (terrainManifestEntryCount <= 0 || terrainManifestEntryCount > 0xFFFF) {
            throw new IllegalArgumentException(
                "Terrain observation manifest entry count must fit positive uint16 IDs"
            );
        }
        this.terrainManifestSha256 = terrainManifestSha256.toLowerCase(Locale.ROOT);
        this.terrainManifestEntryCount = terrainManifestEntryCount;
    }

    public String getTerrainManifestSha256() {
        return terrainManifestSha256;
    }

    public int getTerrainManifestEntryCount() {
        return terrainManifestEntryCount;
    }

    int requireManifestIdentity(int identity) {
        // The versioned block manifest assigns contiguous IDs starting at one.
        if (identity <= 0 || identity > terrainManifestEntryCount) {
            throw new IllegalArgumentException(
                "Terrain identity is absent from the pinned manifest: " + identity
            );
        }
        return identity;
    }

    public static int expectedForegroundLength() {
        return Math.multiplyExact(PIXEL_COUNT, FOREGROUND_CAPACITY);
    }
}
