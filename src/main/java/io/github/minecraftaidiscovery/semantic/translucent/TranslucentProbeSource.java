package io.github.minecraftaidiscovery.semantic.translucent;

public enum TranslucentProbeSource {
    NONE(0, "none"),
    BLOCK_MODEL(1, "block_model"),
    FLUID(2, "fluid_renderer");

    private final int code;
    private final String metadataName;

    TranslucentProbeSource(int code, String metadataName) {
        this.code = code;
        this.metadataName = metadataName;
    }

    public int getCode() {
        return code;
    }

    public String getMetadataName() {
        return metadataName;
    }

    public static TranslucentProbeSource fromCode(int code) {
        for (TranslucentProbeSource source : values()) {
            if (source.code == code) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown translucent probe source code: " + code);
    }
}
