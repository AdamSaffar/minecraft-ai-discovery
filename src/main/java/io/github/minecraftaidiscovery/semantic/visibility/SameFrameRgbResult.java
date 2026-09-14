package io.github.minecraftaidiscovery.semantic.visibility;

public final class SameFrameRgbResult {
    private final int sourceWidth;
    private final int sourceHeight;
    private final int width;
    private final int height;
    private final byte[] rgb;

    SameFrameRgbResult(
        int sourceWidth,
        int sourceHeight,
        int width,
        int height,
        byte[] rgb
    ) {
        if (sourceWidth != SameFrameRgbEncoding.SOURCE_WIDTH
            || sourceHeight != SameFrameRgbEncoding.SOURCE_HEIGHT
            || width != SameFrameRgbEncoding.WIDTH
            || height != SameFrameRgbEncoding.HEIGHT) {
            throw new IllegalArgumentException("Same-frame RGB dimensions are invalid");
        }
        SameFrameRgbEncoding.requireRgb(rgb);
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.width = width;
        this.height = height;
        this.rgb = rgb.clone();
    }

    public int getSourceWidth() {
        return sourceWidth;
    }

    public int getSourceHeight() {
        return sourceHeight;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public byte[] copyRgb() {
        return rgb.clone();
    }
}
