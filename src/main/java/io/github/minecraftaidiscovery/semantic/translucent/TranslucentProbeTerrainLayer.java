package io.github.minecraftaidiscovery.semantic.translucent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum TranslucentProbeTerrainLayer {
    SOLID("solid"),
    CUTOUT_MIPPED("cutout_mipped"),
    CUTOUT("cutout"),
    TRANSLUCENT("translucent");

    private static final List<TranslucentProbeTerrainLayer> SUPPORTED_IN_ORDER =
        Collections.unmodifiableList(Arrays.asList(values()));

    private final String metadataName;

    TranslucentProbeTerrainLayer(String metadataName) {
        this.metadataName = metadataName;
    }

    public String getMetadataName() {
        return metadataName;
    }

    public static List<TranslucentProbeTerrainLayer> supportedInOrder() {
        return SUPPORTED_IN_ORDER;
    }
}
