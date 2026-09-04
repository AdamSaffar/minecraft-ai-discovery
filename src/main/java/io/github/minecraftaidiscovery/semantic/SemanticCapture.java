package io.github.minecraftaidiscovery.semantic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
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
            renderSolidLayer.run();

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
            return writeCapture(client, image);
        }
    }

    private static Path writeCapture(MinecraftClient client, NativeImage encodedImage) throws IOException {
        int width = encodedImage.getWidth();
        int height = encodedImage.getHeight();
        byte[] classIds = new byte[Math.multiplyExact(width, height)];
        long[] pixelCounts = new long[256];
        Path captureDirectory = client.runDirectory.toPath().resolve("semantic-observation").resolve("captures");
        Files.createDirectories(captureDirectory);
        String stem = captureStem();
        Path classBufferPath = captureDirectory.resolve(stem + ".classes.u8");
        Path visualizationPath = captureDirectory.resolve(stem + ".png");
        Path metadataPath = captureDirectory.resolve(stem + ".json");

        try (NativeImage visualization = new NativeImage(width, height, false)) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int markerAlpha = Byte.toUnsignedInt(encodedImage.getPixelOpacity(x, y));
                    int classId = SemanticClassRegistry.classIdForMarkerAlpha(markerAlpha);
                    classIds[y * width + x] = (byte)classId;
                    pixelCounts[classId]++;
                    visualization.setPixelColor(x, y, toAbgr(SemanticClassRegistry.falseColorRgbForClassId(classId)));
                }
            }
            visualization.writeFile(visualizationPath);
        }

        Files.write(classBufferPath, classIds);
        Files.write(metadataPath, metadataJson(width, height, classBufferPath, visualizationPath, pixelCounts).getBytes(StandardCharsets.UTF_8));
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

    private static String metadataJson(
        int width,
        int height,
        Path classBufferPath,
        Path visualizationPath,
        long[] pixelCounts
    ) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"format\": \"semantic-observation-poc-v1\",\n");
        json.append("  \"minecraftVersion\": \"1.16.1\",\n");
        json.append("  \"width\": ").append(width).append(",\n");
        json.append("  \"height\": ").append(height).append(",\n");
        json.append("  \"pixelOrder\": \"row-major, top-left origin\",\n");
        json.append("  \"backgroundClassId\": ").append(SemanticClassRegistry.BACKGROUND_CLASS_ID).append(",\n");
        json.append("  \"classBuffer\": {\"file\": \"").append(classBufferPath.getFileName())
            .append("\", \"encoding\": \"uint8 class id\"},\n");
        json.append("  \"visualization\": {\"file\": \"").append(visualizationPath.getFileName())
            .append("\", \"background\": \"#000000\"},\n");
        json.append("  \"classes\": [\n");
        for (int index = 0; index < SemanticClassRegistry.getClasses().size(); index++) {
            SemanticClass semanticClass = SemanticClassRegistry.getClasses().get(index);
            json.append("    {\"id\": ").append(semanticClass.getClassId())
                .append(", \"registryId\": \"").append(semanticClass.getRegistryId())
                .append("\", \"markerAlpha\": ").append(semanticClass.getMarkerAlpha())
                .append(", \"color\": \"").append(String.format(Locale.ROOT, "#%06X", semanticClass.getFalseColorRgb()))
                .append("\", \"pixelCount\": ").append(pixelCounts[semanticClass.getClassId()]).append("}");
            json.append(index + 1 == SemanticClassRegistry.getClasses().size() ? "\n" : ",\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }
}
