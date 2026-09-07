package io.github.minecraftaidiscovery.semantic.translucent;

import io.github.minecraftaidiscovery.semantic.SemanticClassEncoding;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassVocabulary;
import net.minecraft.client.texture.NativeImage;

public final class TranslucentProbeLayer {
    private final int width;
    private final int height;
    private final int[] classIds;
    private final byte[] sourceKinds;
    private final byte[] sampledAlpha;
    private final float[] depth;

    private TranslucentProbeLayer(
        int width,
        int height,
        int[] classIds,
        byte[] sourceKinds,
        byte[] sampledAlpha,
        float[] depth
    ) {
        int pixels = TranslucentProbeBufferEncoding.requireDimensions(width, height);
        if (classIds == null
            || sourceKinds == null
            || sampledAlpha == null
            || depth == null
            || classIds.length != pixels
            || sourceKinds.length != pixels
            || sampledAlpha.length != pixels
            || depth.length != pixels) {
            throw new IllegalArgumentException("Translucent probe layer dimensions do not match");
        }
        this.width = width;
        this.height = height;
        this.classIds = classIds.clone();
        this.sourceKinds = sourceKinds.clone();
        this.sampledAlpha = sampledAlpha.clone();
        this.depth = depth.clone();
        validate(null);
    }

    public static TranslucentProbeLayer createForTesting(
        int width,
        int height,
        int[] classIds,
        byte[] sourceKinds,
        byte[] sampledAlpha,
        float[] depth
    ) {
        return new TranslucentProbeLayer(
            width,
            height,
            classIds,
            sourceKinds,
            sampledAlpha,
            depth
        );
    }

    public static TranslucentProbeLayer decode(
        NativeImage encodedImage,
        float[] depth,
        BlockClassVocabulary vocabulary
    ) {
        if (encodedImage == null || vocabulary == null) {
            throw new IllegalArgumentException("Translucent probe layer input is missing");
        }
        int width = encodedImage.getWidth();
        int height = encodedImage.getHeight();
        int pixels = Math.multiplyExact(width, height);
        int[] classIds = new int[pixels];
        byte[] sourceKinds = new byte[pixels];
        byte[] sampledAlpha = new byte[pixels];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = y * width + x;
                int encoded = encodedImage.getPixelColor(x, y);
                classIds[pixel] = SemanticClassEncoding.decodeGpuBytes(
                    NativeImage.getRed(encoded),
                    NativeImage.getGreen(encoded)
                );
                sourceKinds[pixel] = (byte)NativeImage.getBlue(encoded);
                sampledAlpha[pixel] = (byte)NativeImage.getAlpha(encoded);
            }
        }

        TranslucentProbeLayer result = new TranslucentProbeLayer(
            width,
            height,
            classIds,
            sourceKinds,
            sampledAlpha,
            depth
        );
        result.validate(vocabulary);
        return result;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getClassId(int pixel) {
        return classIds[pixel];
    }

    public int getSourceCode(int pixel) {
        return Byte.toUnsignedInt(sourceKinds[pixel]);
    }

    public int getSampledAlpha(int pixel) {
        return Byte.toUnsignedInt(sampledAlpha[pixel]);
    }

    public float getDepth(int pixel) {
        return depth[pixel];
    }

    public int[] copyClassIds() {
        return classIds.clone();
    }

    private void validate(BlockClassVocabulary vocabulary) {
        for (int pixel = 0; pixel < classIds.length; pixel++) {
            int classId = SemanticClassEncoding.requireUnsigned16(classIds[pixel]);
            TranslucentProbeSource source = TranslucentProbeSource.fromCode(
                Byte.toUnsignedInt(sourceKinds[pixel])
            );
            int alpha = Byte.toUnsignedInt(sampledAlpha[pixel]);
            if ((classId == BlockClassManifest.BACKGROUND_CLASS_ID)
                != (source == TranslucentProbeSource.NONE)) {
                throw new IllegalArgumentException("Translucent probe pixel has mismatched class and source");
            }
            if (classId == BlockClassManifest.BACKGROUND_CLASS_ID && alpha != 0) {
                throw new IllegalArgumentException("Background translucent probe pixel has nonzero alpha");
            }
            if (classId != BlockClassManifest.BACKGROUND_CLASS_ID && alpha == 0) {
                throw new IllegalArgumentException("Visible translucent probe pixel has zero alpha");
            }
            if (!Float.isFinite(depth[pixel]) || depth[pixel] < 0.0F || depth[pixel] > 1.0F) {
                throw new IllegalArgumentException("Translucent probe pixel has invalid window depth");
            }
            if (vocabulary != null && classId != BlockClassManifest.BACKGROUND_CLASS_ID) {
                vocabulary.requireRegistryIdentifier(classId);
            }
        }
    }
}
