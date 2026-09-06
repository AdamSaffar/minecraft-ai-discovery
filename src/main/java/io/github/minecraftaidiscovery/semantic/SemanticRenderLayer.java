package io.github.minecraftaidiscovery.semantic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum SemanticRenderLayer {
    SOLID("solid", false, false),
    CUTOUT_MIPPED("cutout_mipped", true, true),
    CUTOUT("cutout", true, false);

    private static final List<SemanticRenderLayer> SUPPORTED_IN_ORDER =
        Collections.unmodifiableList(Arrays.asList(values()));

    private final String metadataName;
    private final boolean textureCoverage;
    private final boolean mipmaps;

    SemanticRenderLayer(String metadataName, boolean textureCoverage, boolean mipmaps) {
        this.metadataName = metadataName;
        this.textureCoverage = textureCoverage;
        this.mipmaps = mipmaps;
    }

    public String getMetadataName() {
        return metadataName;
    }

    public boolean usesTextureCoverage() {
        return textureCoverage;
    }

    public boolean usesMipmaps() {
        return mipmaps;
    }

    public static List<SemanticRenderLayer> supportedInOrder() {
        return SUPPORTED_IN_ORDER;
    }

    public static void requireCanonicalOrder(List<SemanticRenderLayer> layers) {
        if (layers == null || layers.isEmpty()) {
            throw new IllegalArgumentException("Semantic render layers are missing");
        }

        int previousOrdinal = -1;
        for (SemanticRenderLayer layer : layers) {
            if (layer == null) {
                throw new IllegalArgumentException("Semantic render layer is missing");
            }
            if (layer.ordinal() <= previousOrdinal) {
                throw new IllegalArgumentException(
                    "Semantic render layers are not in canonical order or contain a duplicate"
                );
            }
            previousOrdinal = layer.ordinal();
        }
    }
}
