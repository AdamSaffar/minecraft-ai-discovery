package io.github.minecraftaidiscovery.semantic;

import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifestRuntime;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassVocabulary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;

public final class SemanticCapture {
    private static final AtomicInteger CAPTURE_SEQUENCE = new AtomicInteger();

    private SemanticCapture() {
    }

    public static Path capture(MinecraftClient client, Runnable renderSolidLayer) throws IOException {
        Framebuffer mainFramebuffer = client.getFramebuffer();
        int width = mainFramebuffer.viewportWidth;
        int height = mainFramebuffer.viewportHeight;
        Framebuffer semanticFramebuffer = new Framebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
        NativeImage encodedImage = null;

        try {
            semanticFramebuffer.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            semanticFramebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
            semanticFramebuffer.beginWrite(true);
            SemanticRenderPass.run(renderSolidLayer);

            semanticFramebuffer.beginRead();
            encodedImage = new NativeImage(width, height, false);
            encodedImage.loadFromTextureImage(0, false);
            semanticFramebuffer.endRead();
            encodedImage.mirrorVertically();
        } finally {
            try {
                semanticFramebuffer.delete();
            } finally {
                mainFramebuffer.beginWrite(true);
            }
        }

        if (encodedImage == null) {
            throw new IOException("The semantic framebuffer could not be read");
        }

        try (NativeImage image = encodedImage) {
            return writeCapture(client, image, BlockClassManifestRuntime.require());
        }
    }

    private static Path writeCapture(
        MinecraftClient client,
        NativeImage encodedImage,
        BlockClassVocabulary vocabulary
    ) throws IOException {
        int width = encodedImage.getWidth();
        int height = encodedImage.getHeight();
        int[] classIds = new int[Math.multiplyExact(width, height)];
        Map<Integer, Long> pixelCounts = new TreeMap<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int encodedPixel = encodedImage.getPixelColor(x, y);
                int classId = SemanticClassEncoding.decodeGpuBytes(
                    NativeImage.getRed(encodedPixel),
                    NativeImage.getGreen(encodedPixel)
                );
                if (classId != BlockClassManifest.BACKGROUND_CLASS_ID) {
                    vocabulary.requireRegistryIdentifier(classId);
                    if (NativeImage.getBlue(encodedPixel) != 0 || NativeImage.getAlpha(encodedPixel) != 0xFF) {
                        throw new IllegalStateException(
                            "Semantic GPU transport guard channels were modified for class ID " + classId
                        );
                    }
                }
                classIds[y * width + x] = classId;
                pixelCounts.put(classId, pixelCounts.getOrDefault(classId, 0L) + 1L);
            }
        }

        byte[] classBuffer = SemanticClassEncoding.encodeU16Le(width, height, classIds);
        Path captureDirectory = client.runDirectory.toPath().resolve("semantic-observation").resolve("captures");
        String stem = captureStem();
        Path classBufferPath = captureDirectory.resolve(stem + ".classes.u16le");
        Path visualizationPath = captureDirectory.resolve(stem + ".png");
        Path metadataPath = captureDirectory.resolve(stem + ".json");
        String metadata = SemanticCaptureMetadata.toJson(
            width,
            height,
            classBufferPath.getFileName().toString(),
            classBuffer.length,
            visualizationPath.getFileName().toString(),
            vocabulary,
            pixelCounts,
            Collections.singletonList(SemanticRenderLayer.SOLID),
            client.options.graphicsMode.toString()
        );

        Files.createDirectories(captureDirectory);
        try (NativeImage visualization = new NativeImage(width, height, false)) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int classId = classIds[y * width + x];
                    visualization.setPixelColor(x, y, toAbgr(SemanticClassEncoding.falseColorRgb(classId)));
                }
            }
            visualization.writeFile(visualizationPath);
        }

        Files.write(classBufferPath, classBuffer);
        Files.write(metadataPath, metadata.getBytes(StandardCharsets.UTF_8));
        return metadataPath;
    }

    private static String captureStem() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return "semantic-" + format.format(new Date()) + "-" + CAPTURE_SEQUENCE.incrementAndGet();
    }

    private static int toAbgr(int rgb) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        return 0xFF000000 | blue << 16 | green << 8 | red;
    }
}
