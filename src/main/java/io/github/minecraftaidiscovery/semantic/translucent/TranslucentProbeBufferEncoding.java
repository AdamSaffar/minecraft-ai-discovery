package io.github.minecraftaidiscovery.semantic.translucent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class TranslucentProbeBufferEncoding {
    public static final String BYTE_ORDER = "little-endian";
    public static final String PIXEL_ORDER = "row-major, top-left origin";
    public static final String DEPTH_ENCODING = "float32le window depth [0,1]";

    private TranslucentProbeBufferEncoding() {
    }

    public static byte[] encodeFloat32Le(int width, int height, float[] values) {
        int pixels = requireDimensions(width, height);
        if (values == null || values.length != pixels) {
            throw new IllegalArgumentException("Float diagnostic buffer dimensions do not match");
        }
        ByteBuffer encoded = ByteBuffer.allocate(Math.multiplyExact(pixels, Float.BYTES))
            .order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Float diagnostic buffer contains a non-finite value");
            }
            encoded.putFloat(value);
        }
        return encoded.array();
    }

    public static byte[] requireU8(int width, int height, byte[] values) {
        int pixels = requireDimensions(width, height);
        if (values == null || values.length != pixels) {
            throw new IllegalArgumentException("Unsigned-byte diagnostic buffer dimensions do not match");
        }
        return values.clone();
    }

    public static byte[] encodeLayer(TranslucentProbeLayer layer) {
        if (layer == null) {
            throw new IllegalArgumentException("Translucent probe layer is missing");
        }
        int pixels = requireDimensions(layer.getWidth(), layer.getHeight());
        ByteBuffer encoded = ByteBuffer.allocate(Math.multiplyExact(pixels, 8))
            .order(ByteOrder.LITTLE_ENDIAN);
        for (int pixel = 0; pixel < pixels; pixel++) {
            encoded.putShort((short)layer.getClassId(pixel));
            encoded.put((byte)layer.getSourceCode(pixel));
            encoded.put((byte)layer.getSampledAlpha(pixel));
            encoded.putFloat(layer.getDepth(pixel));
        }
        return encoded.array();
    }

    public static int requireDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Diagnostic buffer dimensions must be positive");
        }
        return Math.multiplyExact(width, height);
    }
}
