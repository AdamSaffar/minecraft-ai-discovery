package io.github.minecraftaidiscovery.semantic.visibility;

public final class SameFrameRgbEncoding {
    public static final int SOURCE_WIDTH = 1920;
    public static final int SOURCE_HEIGHT = 1080;
    public static final int WIDTH = 640;
    public static final int HEIGHT = 360;
    public static final int SOURCE_BYTES_PER_PIXEL = 4;
    public static final int RGB_BYTES_PER_PIXEL = 3;
    public static final int DOWNSAMPLE_FACTOR = 3;
    public static final String ENCODING =
        "uint8 RGB pre-HUD framebuffer; top-left rows from 3x3 byte-space box averaging";

    private static final int SAMPLES_PER_OUTPUT_PIXEL =
        DOWNSAMPLE_FACTOR * DOWNSAMPLE_FACTOR;

    private SameFrameRgbEncoding() {
    }

    public static SameFrameRgbResult encodeBottomToTopRgba(
        int sourceWidth,
        int sourceHeight,
        byte[] bottomToTopRgba
    ) {
        requireSourceDimensions(sourceWidth, sourceHeight);
        int expected = expectedSourceLength();
        if (bottomToTopRgba == null || bottomToTopRgba.length != expected) {
            throw new IllegalArgumentException(
                "Expected " + expected + " same-frame RGBA bytes"
            );
        }

        byte[] topToBottomRgb = new byte[expectedRgbLength()];
        for (int destinationY = 0; destinationY < HEIGHT; destinationY++) {
            // OpenGL starts at the bottom; each output row consumes three source rows.
            int sourceYStart = SOURCE_HEIGHT
                - (destinationY + 1) * DOWNSAMPLE_FACTOR;
            for (int destinationX = 0; destinationX < WIDTH; destinationX++) {
                int sourceXStart = destinationX * DOWNSAMPLE_FACTOR;
                int destinationOffset = (destinationY * WIDTH + destinationX)
                    * RGB_BYTES_PER_PIXEL;
                for (int channel = 0; channel < RGB_BYTES_PER_PIXEL; channel++) {
                    int sum = 0;
                    for (int sourceY = sourceYStart;
                        sourceY < sourceYStart + DOWNSAMPLE_FACTOR;
                        sourceY++) {
                        int rowOffset = sourceY * SOURCE_WIDTH * SOURCE_BYTES_PER_PIXEL;
                        for (int sourceX = sourceXStart;
                            sourceX < sourceXStart + DOWNSAMPLE_FACTOR;
                            sourceX++) {
                            int sourceOffset = rowOffset
                                + sourceX * SOURCE_BYTES_PER_PIXEL
                                + channel;
                            sum += Byte.toUnsignedInt(bottomToTopRgba[sourceOffset]);
                        }
                    }
                    // The calibration averages stored bytes, not reconstructed linear light.
                    topToBottomRgb[destinationOffset + channel] = (byte)(
                        (sum + SAMPLES_PER_OUTPUT_PIXEL / 2) / SAMPLES_PER_OUTPUT_PIXEL
                    );
                }
            }
        }
        return new SameFrameRgbResult(
            SOURCE_WIDTH,
            SOURCE_HEIGHT,
            WIDTH,
            HEIGHT,
            topToBottomRgb
        );
    }

    public static int expectedSourceLength() {
        return Math.multiplyExact(
            Math.multiplyExact(SOURCE_WIDTH, SOURCE_HEIGHT),
            SOURCE_BYTES_PER_PIXEL
        );
    }

    public static int expectedRgbLength() {
        return Math.multiplyExact(
            Math.multiplyExact(WIDTH, HEIGHT),
            RGB_BYTES_PER_PIXEL
        );
    }

    static byte[] requireRgb(byte[] rgb) {
        int expected = expectedRgbLength();
        if (rgb == null || rgb.length != expected) {
            throw new IllegalArgumentException(
                "Expected " + expected + " same-frame RGB bytes"
            );
        }
        return rgb;
    }

    private static void requireSourceDimensions(int sourceWidth, int sourceHeight) {
        if (sourceWidth != SOURCE_WIDTH || sourceHeight != SOURCE_HEIGHT) {
            throw new IllegalArgumentException(
                "Same-frame RGB source must be exactly "
                    + SOURCE_WIDTH + "x" + SOURCE_HEIGHT
            );
        }
    }
}
