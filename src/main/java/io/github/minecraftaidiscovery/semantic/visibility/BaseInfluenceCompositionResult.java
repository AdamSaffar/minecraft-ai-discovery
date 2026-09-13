package io.github.minecraftaidiscovery.semantic.visibility;

public final class BaseInfluenceCompositionResult {
    private final int width;
    private final int height;
    private final byte[] rgb;
    private final byte[] reasons;

    BaseInfluenceCompositionResult(int width, int height, byte[] rgb, byte[] reasons) {
        int pixels = BaseInfluenceComposition.requireDimensions(width, height);
        if (rgb == null || rgb.length != Math.multiplyExact(pixels, 3)) {
            throw new IllegalArgumentException("Base-influence RGB buffer length is invalid");
        }
        if (reasons == null || reasons.length != pixels) {
            throw new IllegalArgumentException("Base-influence reason buffer length is invalid");
        }
        for (byte reason : reasons) {
            BaseInfluenceReasonEncoding.requireCode(Byte.toUnsignedInt(reason));
        }
        this.width = width;
        this.height = height;
        this.rgb = rgb.clone();
        this.reasons = reasons.clone();
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

    public byte[] copyReasons() {
        return reasons.clone();
    }
}
