package io.github.minecraftaidiscovery.semantic.translucent;

public enum TranslucentProbePassMode {
    FRONTMOST(0),
    DEPTH_PEEL(1),
    ACCUMULATED_OPACITY(2),
    ACCEPTED_FRAGMENT_COUNT(3),
    SOLID_FLUID(4);

    private final int shaderCode;

    TranslucentProbePassMode(int shaderCode) {
        this.shaderCode = shaderCode;
    }

    int getShaderCode() {
        return shaderCode;
    }

    public boolean usesPreviousDepth() {
        return this == DEPTH_PEEL;
    }
}
