package io.github.minecraftaidiscovery.semantic.firstperson;

public final class FirstPersonDepthCoverageResult {
    private final int width;
    private final int height;
    private final byte[] fullScreenCoverage;
    private final boolean postHandDepthSampled;

    public FirstPersonDepthCoverageResult(
        int width,
        int height,
        byte[] fullScreenCoverage,
        boolean postHandDepthSampled
    ) {
        FirstPersonDepthCoverageEncoding.requireBinary(width, height, fullScreenCoverage);
        if (!postHandDepthSampled
            && FirstPersonDepthCoverageEncoding.countCovered(fullScreenCoverage) != 0) {
            throw new IllegalArgumentException(
                "Depth coverage cannot exist without a post-hand sample"
            );
        }
        this.width = width;
        this.height = height;
        this.fullScreenCoverage = fullScreenCoverage.clone();
        this.postHandDepthSampled = postHandDepthSampled;
    }

    public static FirstPersonDepthCoverageResult noFirstPersonStage(int width, int height) {
        return new FirstPersonDepthCoverageResult(
            width,
            height,
            new byte[FirstPersonDepthCoverageEncoding.requireDimensions(width, height)],
            false
        );
    }

    public byte[] copyFullScreenCoverage() {
        return fullScreenCoverage.clone();
    }

    public boolean wasPostHandDepthSampled() {
        return postHandDepthSampled;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
