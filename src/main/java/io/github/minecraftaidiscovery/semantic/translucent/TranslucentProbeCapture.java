package io.github.minecraftaidiscovery.semantic.translucent;

import io.github.minecraftaidiscovery.semantic.SemanticClassEncoding;
import io.github.minecraftaidiscovery.semantic.SemanticMinecraftRenderLayers;
import io.github.minecraftaidiscovery.semantic.SemanticRenderLayer;
import io.github.minecraftaidiscovery.semantic.SemanticRenderPass;
import io.github.minecraftaidiscovery.semantic.lighting.LightingRetentionBufferEncoding;
import io.github.minecraftaidiscovery.semantic.lighting.LightingRetentionCaptureContext;
import io.github.minecraftaidiscovery.semantic.lighting.LightingRetentionRenderPass;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifestRuntime;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassVocabulary;
import io.github.minecraftaidiscovery.semantic.overlay.CameraOverlayRetentionBufferEncoding;
import io.github.minecraftaidiscovery.semantic.overlay.CameraOverlayRetentionResult;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class TranslucentProbeCapture {
    public static final int WIDTH = 640;
    public static final int HEIGHT = 360;

    private static final int LAYER_RECORD_BYTES_PER_PIXEL = 8;
    private static final AtomicInteger CAPTURE_SEQUENCE = new AtomicInteger();

    private TranslucentProbeCapture() {
    }

    public static PreparedCapture prepare(
        MinecraftClient client,
        String cameraMedium,
        FogRetentionCaptureContext fogContext,
        LightingRetentionCaptureContext lightingContext,
        Consumer<RenderLayer> renderLayer
    ) throws IOException {
        if (!TranslucentProbeConfiguration.isEnabled()) {
            throw new IllegalStateException("Translucent probe is not enabled");
        }
        if (client == null
            || fogContext == null
            || lightingContext == null
            || renderLayer == null) {
            throw new IllegalArgumentException("Translucent probe capture input is missing");
        }

        Framebuffer mainFramebuffer = client.getFramebuffer();
        List<Framebuffer> framebuffers = new ArrayList<>();
        BlockClassVocabulary vocabulary = BlockClassManifestRuntime.require();
        Collection<String> resourcePacks = client.getResourcePackManager().getEnabledNames();
        if (resourcePacks.size() != 1 || !resourcePacks.contains("vanilla")) {
            throw new IllegalStateException(
                "Researcher probe supports only the vanilla resource pack"
            );
        }
        Path captureDirectory = client.runDirectory.toPath()
            .resolve("semantic-observation")
            .resolve("translucent-probe")
            .resolve(captureStem());

        try {
            Framebuffer base = framebuffer(framebuffers);
            prepare(base, null);
            for (SemanticRenderLayer layer : SemanticRenderLayer.supportedInOrder()) {
                SemanticRenderPass.run(
                    layer,
                    () -> renderLayer.accept(SemanticMinecraftRenderLayers.requireMinecraftLayer(layer))
                );
            }
            int[] baseClassIds = readBaseClasses(base, vocabulary);
            float[] baseDepth = readDepth(base);

            Framebuffer lightingRetentionTarget = framebuffer(framebuffers);
            prepare(lightingRetentionTarget, base);
            for (SemanticRenderLayer layer : SemanticRenderLayer.supportedInOrder()) {
                LightingRetentionRenderPass.run(
                    layer,
                    lightingRetentionTarget,
                    () -> renderLayer.accept(
                        SemanticMinecraftRenderLayers.requireMinecraftLayer(layer)
                    )
                );
            }
            LightingReadback lighting = readLightingRetention(
                lightingRetentionTarget,
                baseClassIds
            );

            Framebuffer fogRetentionTarget = framebuffer(framebuffers);
            prepare(fogRetentionTarget, null);
            for (SemanticRenderLayer layer : SemanticRenderLayer.supportedInOrder()) {
                FogRetentionRenderPass.run(
                    layer,
                    fogRetentionTarget,
                    fogContext.getParameters(),
                    () -> renderLayer.accept(
                        SemanticMinecraftRenderLayers.requireMinecraftLayer(layer)
                    )
                );
            }
            byte[] baseFogRetention = readFogRetention(fogRetentionTarget, baseClassIds);

            // Lava uses the fluid renderer but Minecraft 1.16.1 routes it through the solid layer.
            Framebuffer solidFluidTarget = framebuffer(framebuffers);
            prepare(solidFluidTarget, base);
            renderProbeLayer(
                RenderLayer.getSolid(),
                TranslucentProbePassMode.SOLID_FLUID,
                solidFluidTarget,
                -1,
                renderLayer
            );
            TranslucentProbeLayer solidFluid = readLayer(solidFluidTarget, vocabulary);

            List<TranslucentProbeLayer> storedLayers = new ArrayList<>();
            Framebuffer previous = null;
            TranslucentProbeLayer overflowLayer = null;
            // The extra peel is an overflow detector and is never serialized as an additional candidate slot.
            int passCount = TranslucentProbeCandidates.TEMPORARY_STORAGE_CAPACITY + 1;
            for (int pass = 0; pass < passCount; pass++) {
                Framebuffer target = framebuffer(framebuffers);
                prepare(target, base);
                TranslucentProbePassMode mode = pass == 0
                    ? TranslucentProbePassMode.FRONTMOST
                    : TranslucentProbePassMode.DEPTH_PEEL;
                renderProbeLayer(
                    RenderLayer.getTranslucent(),
                    mode,
                    target,
                    previous == null ? -1 : previous.depthAttachment,
                    renderLayer
                );
                TranslucentProbeLayer layer = readLayer(target, vocabulary);
                if (pass < TranslucentProbeCandidates.TEMPORARY_STORAGE_CAPACITY) {
                    storedLayers.add(layer);
                } else {
                    overflowLayer = layer;
                }
                previous = target;
            }
            TranslucentProbeCandidates candidates = TranslucentProbeCandidates.generate(
                storedLayers,
                overflowLayer
            );

            Framebuffer opacityTarget = framebuffer(framebuffers);
            prepare(opacityTarget, base);
            renderProbeLayer(
                RenderLayer.getTranslucent(),
                TranslucentProbePassMode.ACCUMULATED_OPACITY,
                opacityTarget,
                -1,
                renderLayer
            );
            byte[] accumulatedOpacity = readColorChannel(opacityTarget, ColorChannel.ALPHA);

            Framebuffer countTarget = framebuffer(framebuffers);
            prepare(countTarget, base);
            renderProbeLayer(
                RenderLayer.getTranslucent(),
                TranslucentProbePassMode.ACCEPTED_FRAGMENT_COUNT,
                countTarget,
                -1,
                renderLayer
            );
            byte[] acceptedFragmentCount = readColorChannel(countTarget, ColorChannel.RED);

            return new PreparedCapture(
                captureDirectory,
                client.options.graphicsMode.toString(),
                cameraMedium,
                resourcePacks,
                fogContext,
                lightingContext,
                vocabulary,
                baseClassIds,
                baseDepth,
                lighting.rgb,
                lighting.validity,
                lighting.validBasePixelCount,
                lighting.unsupportedBasePixelCount,
                baseFogRetention,
                solidFluid,
                storedLayers,
                candidates,
                accumulatedOpacity,
                acceptedFragmentCount
            );
        } finally {
            for (int index = framebuffers.size() - 1; index >= 0; index--) {
                framebuffers.get(index).delete();
            }
            mainFramebuffer.beginWrite(true);
        }
    }

    private static Path writeCapture(
        Path captureDirectory,
        String graphicsMode,
        String cameraMedium,
        Collection<String> resourcePacks,
        FogRetentionCaptureContext fogContext,
        LightingRetentionCaptureContext lightingContext,
        BlockClassVocabulary vocabulary,
        int[] baseClassIds,
        float[] baseDepth,
        byte[] baseLightingRetention,
        byte[] baseLightingValidity,
        long validLightingBasePixelCount,
        long unsupportedLightingBasePixelCount,
        byte[] baseFogRetention,
        TranslucentProbeLayer solidFluid,
        List<TranslucentProbeLayer> storedLayers,
        TranslucentProbeCandidates candidates,
        byte[] accumulatedOpacity,
        byte[] acceptedFragmentCount,
        CameraOverlayRetentionResult cameraOverlayRetention
    ) throws IOException {
        Files.createDirectories(captureDirectory);
        byte[] baseClasses = SemanticClassEncoding.encodeU16Le(WIDTH, HEIGHT, baseClassIds);
        byte[] encodedBaseDepth = TranslucentProbeBufferEncoding.encodeFloat32Le(
            WIDTH,
            HEIGHT,
            baseDepth
        );
        byte[] encodedSolidFluid = TranslucentProbeBufferEncoding.encodeLayer(solidFluid);
        byte[] frontToBack = candidates.getFrontToBack();
        byte[] ranked = candidates.getContributionRanked();
        byte[] overflow = candidates.getOverflow();
        accumulatedOpacity = TranslucentProbeBufferEncoding.requireU8(
            WIDTH,
            HEIGHT,
            accumulatedOpacity
        );
        acceptedFragmentCount = TranslucentProbeBufferEncoding.requireU8(
            WIDTH,
            HEIGHT,
            acceptedFragmentCount
        );
        baseFogRetention = TranslucentProbeBufferEncoding.requireU8(
            WIDTH,
            HEIGHT,
            baseFogRetention
        );
        baseLightingRetention = LightingRetentionBufferEncoding.requireRgb(
            WIDTH,
            HEIGHT,
            baseLightingRetention
        );
        baseLightingValidity = TranslucentProbeBufferEncoding.requireU8(
            WIDTH,
            HEIGHT,
            baseLightingValidity
        );
        if (cameraOverlayRetention == null
            || cameraOverlayRetention.getWidth() != WIDTH
            || cameraOverlayRetention.getHeight() != HEIGHT) {
            throw new IllegalArgumentException(
                "Camera-overlay retention dimensions do not match the F9 capture"
            );
        }
        byte[] baseCameraOverlayRetention =
            CameraOverlayRetentionBufferEncoding.maskToPositiveBase(
                WIDTH,
                HEIGHT,
                cameraOverlayRetention.copyFullScreenRetention(),
                baseClassIds
            );
        long validCameraOverlayBasePixelCount = Arrays.stream(baseClassIds)
            .filter(classId -> classId != BlockClassManifest.BACKGROUND_CLASS_ID)
            .count();

        String baseClassesFile = "base.classes.u16le";
        String baseDepthFile = "base.depth.f32le";
        String baseLightingRetentionFile = "base.lighting-retention.rgb8";
        String baseLightingValidityFile = "base.lighting-validity.u8";
        String baseFogRetentionFile = "base.fog-retention.u8";
        String baseCameraOverlayRetentionFile = "base.camera-overlay-retention.u8";
        String solidFluidFile = "solid-fluid.frontmost.bin";
        String frontToBackFile = "translucent.front-to-back.bin";
        String rankedFile = "translucent.contribution-ranked.bin";
        String opacityFile = "translucent.accumulated-opacity.u8";
        String countFile = "translucent.accepted-fragment-count.u8";
        String overflowFile = "translucent.storage-overflow.u8";
        String baseVisualizationFile = "base.png";
        String lightingRetentionVisualizationFile = "base-lighting-retention.png";
        String fogRetentionVisualizationFile = "base-fog-retention.png";
        String cameraOverlayRetentionVisualizationFile =
            "base-camera-overlay-retention.png";
        String nearestVisualizationFile = "translucent-frontmost.png";

        write(captureDirectory, baseClassesFile, baseClasses);
        write(captureDirectory, baseDepthFile, encodedBaseDepth);
        write(captureDirectory, baseLightingRetentionFile, baseLightingRetention);
        write(captureDirectory, baseLightingValidityFile, baseLightingValidity);
        write(captureDirectory, baseFogRetentionFile, baseFogRetention);
        write(
            captureDirectory,
            baseCameraOverlayRetentionFile,
            baseCameraOverlayRetention
        );
        write(captureDirectory, solidFluidFile, encodedSolidFluid);
        write(captureDirectory, frontToBackFile, frontToBack);
        write(captureDirectory, rankedFile, ranked);
        write(captureDirectory, opacityFile, accumulatedOpacity);
        write(captureDirectory, countFile, acceptedFragmentCount);
        write(captureDirectory, overflowFile, overflow);
        writeVisualization(captureDirectory.resolve(baseVisualizationFile), baseClassIds);
        writeRgbVisualization(
            captureDirectory.resolve(lightingRetentionVisualizationFile),
            baseLightingRetention
        );
        writeGrayscaleVisualization(
            captureDirectory.resolve(fogRetentionVisualizationFile),
            baseFogRetention
        );
        writeGrayscaleVisualization(
            captureDirectory.resolve(cameraOverlayRetentionVisualizationFile),
            baseCameraOverlayRetention
        );
        writeVisualization(
            captureDirectory.resolve(nearestVisualizationFile),
            storedLayers.get(0).copyClassIds()
        );

        LinkedHashMap<String, TranslucentProbeMetadata.Artifact> artifacts = new LinkedHashMap<>();
        artifacts.put(
            "baseClasses",
            artifact(baseClassesFile, SemanticClassEncoding.CLASS_BUFFER_ENCODING, 2, baseClasses)
        );
        artifacts.put(
            "baseDepth",
            artifact(baseDepthFile, TranslucentProbeBufferEncoding.DEPTH_ENCODING, 4, encodedBaseDepth)
        );
        artifacts.put(
            "baseLightingRetention",
            artifact(
                baseLightingRetentionFile,
                LightingRetentionBufferEncoding.RGB_ENCODING,
                LightingRetentionBufferEncoding.RGB_BYTES_PER_PIXEL,
                baseLightingRetention
            )
        );
        artifacts.put(
            "baseLightingValidity",
            artifact(
                baseLightingValidityFile,
                LightingRetentionBufferEncoding.VALIDITY_ENCODING,
                1,
                baseLightingValidity
            )
        );
        artifacts.put(
            "baseFogRetention",
            artifact(
                baseFogRetentionFile,
                "uint8 fixed-function fog retention; 0 fully fogged, 255 unfogged",
                1,
                baseFogRetention
            )
        );
        artifacts.put(
            "baseCameraOverlayRetention",
            artifact(
                baseCameraOverlayRetentionFile,
                CameraOverlayRetentionBufferEncoding.ENCODING,
                1,
                baseCameraOverlayRetention
            )
        );
        artifacts.put(
            "solidFluidFrontmost",
            artifact(
                solidFluidFile,
                "uint16le class, uint8 source, uint8 sampled alpha, float32le window depth",
                LAYER_RECORD_BYTES_PER_PIXEL,
                encodedSolidFluid
            )
        );
        artifacts.put(
            "frontToBackCandidates",
            artifact(
                frontToBackFile,
                TranslucentProbeCandidates.FRONT_TO_BACK_ENCODING,
                TranslucentProbeCandidates.TEMPORARY_STORAGE_CAPACITY
                    * TranslucentProbeCandidates.BYTES_PER_SLOT,
                frontToBack
            )
        );
        artifacts.put(
            "contributionRankedCandidates",
            artifact(
                rankedFile,
                TranslucentProbeCandidates.CONTRIBUTION_RANKED_ENCODING,
                TranslucentProbeCandidates.TEMPORARY_STORAGE_CAPACITY
                    * TranslucentProbeCandidates.BYTES_PER_SLOT,
                ranked
            )
        );
        artifacts.put("accumulatedOpacity", artifact(opacityFile, "uint8", 1, accumulatedOpacity));
        artifacts.put(
            "acceptedFragmentCount",
            artifact(countFile, "uint8 saturated at 255", 1, acceptedFragmentCount)
        );
        artifacts.put("storageOverflow", artifact(overflowFile, "uint8 boolean", 1, overflow));

        long saturatedFragmentCountPixels = countValue(acceptedFragmentCount, 0xFF);
        String metadata = TranslucentProbeMetadata.toJson(
            WIDTH,
            HEIGHT,
            graphicsMode,
            cameraMedium,
            resourcePacks,
            fogContext,
            lightingContext,
            vocabulary,
            artifacts,
            baseVisualizationFile,
            lightingRetentionVisualizationFile,
            fogRetentionVisualizationFile,
            cameraOverlayRetentionVisualizationFile,
            nearestVisualizationFile,
            validLightingBasePixelCount,
            unsupportedLightingBasePixelCount,
            validCameraOverlayBasePixelCount,
            candidates.getOverflowPixelCount(),
            saturatedFragmentCountPixels,
            cameraOverlayRetention,
            observedClasses(solidFluid, storedLayers)
        );
        Path metadataPath = captureDirectory.resolve("metadata.json");
        Files.write(metadataPath, metadata.getBytes(StandardCharsets.UTF_8));
        return metadataPath;
    }

    private static Framebuffer framebuffer(List<Framebuffer> owned) {
        Framebuffer framebuffer = new Framebuffer(
            WIDTH,
            HEIGHT,
            true,
            MinecraftClient.IS_SYSTEM_MAC
        );
        framebuffer.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        owned.add(framebuffer);
        return framebuffer;
    }

    private static void prepare(Framebuffer target, Framebuffer baseDepth) {
        target.clear(MinecraftClient.IS_SYSTEM_MAC);
        if (baseDepth != null) {
            target.copyDepthFrom(baseDepth);
        }
        target.beginWrite(true);
    }

    private static void renderProbeLayer(
        RenderLayer layer,
        TranslucentProbePassMode mode,
        Framebuffer target,
        int previousDepthTexture,
        Consumer<RenderLayer> renderer
    ) {
        TranslucentProbeRenderPass.run(
            layer,
            mode,
            target,
            previousDepthTexture,
            () -> renderer.accept(layer)
        );
    }

    private static int[] readBaseClasses(
        Framebuffer framebuffer,
        BlockClassVocabulary vocabulary
    ) {
        try (NativeImage image = readColor(framebuffer)) {
            int[] classIds = new int[Math.multiplyExact(WIDTH, HEIGHT)];
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int pixel = y * WIDTH + x;
                    int encoded = image.getPixelColor(x, y);
                    int classId = SemanticClassEncoding.decodeGpuBytes(
                        NativeImage.getRed(encoded),
                        NativeImage.getGreen(encoded)
                    );
                    SemanticClassEncoding.requireValidGpuPixel(
                        classId,
                        NativeImage.getBlue(encoded),
                        NativeImage.getAlpha(encoded)
                    );
                    if (classId != BlockClassManifest.BACKGROUND_CLASS_ID) {
                        vocabulary.requireRegistryIdentifier(classId);
                    }
                    classIds[pixel] = classId;
                }
            }
            return classIds;
        }
    }

    private static TranslucentProbeLayer readLayer(
        Framebuffer framebuffer,
        BlockClassVocabulary vocabulary
    ) {
        float[] depth = readDepth(framebuffer);
        try (NativeImage image = readColor(framebuffer)) {
            return TranslucentProbeLayer.decode(image, depth, vocabulary);
        }
    }

    private static byte[] readColorChannel(Framebuffer framebuffer, ColorChannel channel) {
        try (NativeImage image = readColor(framebuffer)) {
            byte[] values = new byte[Math.multiplyExact(WIDTH, HEIGHT)];
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int encoded = image.getPixelColor(x, y);
                    int value = channel == ColorChannel.RED
                        ? NativeImage.getRed(encoded)
                        : NativeImage.getAlpha(encoded);
                    values[y * WIDTH + x] = (byte)value;
                }
            }
            return values;
        }
    }

    private static byte[] readFogRetention(Framebuffer framebuffer, int[] baseClassIds) {
        if (baseClassIds.length != Math.multiplyExact(WIDTH, HEIGHT)) {
            throw new IllegalArgumentException("Base class buffer dimensions do not match");
        }
        try (NativeImage image = readColor(framebuffer)) {
            byte[] values = new byte[baseClassIds.length];
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int pixel = y * WIDTH + x;
                    int encoded = image.getPixelColor(x, y);
                    int red = NativeImage.getRed(encoded);
                    int green = NativeImage.getGreen(encoded);
                    int blue = NativeImage.getBlue(encoded);
                    int alpha = NativeImage.getAlpha(encoded);
                    boolean basePresent = baseClassIds[pixel]
                        != BlockClassManifest.BACKGROUND_CLASS_ID;
                    if (red != green || red != blue) {
                        throw new IllegalStateException(
                            "Fog retention framebuffer modified its grayscale channels"
                        );
                    }
                    if (basePresent && alpha < SemanticClassEncoding.MIN_VISIBLE_CUTOUT_ALPHA) {
                        throw new IllegalStateException(
                            "Fog retention coverage does not match the winning base fragment"
                        );
                    }
                    // Background-only geometry still participates in the replay depth test,
                    // but it must not become a retained base value in the saved diagnostic.
                    values[pixel] = basePresent ? (byte)red : 0;
                }
            }
            return values;
        }
    }

    private static LightingReadback readLightingRetention(
        Framebuffer framebuffer,
        int[] baseClassIds
    ) {
        int pixels = Math.multiplyExact(WIDTH, HEIGHT);
        if (baseClassIds.length != pixels) {
            throw new IllegalArgumentException("Base class buffer dimensions do not match");
        }
        try (NativeImage image = readColor(framebuffer)) {
            byte[] rgb = new byte[Math.multiplyExact(pixels, 3)];
            byte[] renderedAlpha = new byte[pixels];
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int pixel = y * WIDTH + x;
                    int encoded = image.getPixelColor(x, y);
                    renderedAlpha[pixel] = (byte)NativeImage.getAlpha(encoded);
                }
            }
            byte[] validity = LightingRetentionBufferEncoding.validityForSupportedBase(
                WIDTH,
                HEIGHT,
                baseClassIds,
                renderedAlpha
            );
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int pixel = y * WIDTH + x;
                    if (validity[pixel] == 0) {
                        continue;
                    }
                    int encoded = image.getPixelColor(x, y);
                    int offset = pixel * LightingRetentionBufferEncoding.RGB_BYTES_PER_PIXEL;
                    rgb[offset] = (byte)NativeImage.getRed(encoded);
                    rgb[offset + 1] = (byte)NativeImage.getGreen(encoded);
                    rgb[offset + 2] = (byte)NativeImage.getBlue(encoded);
                }
            }
            long validBasePixelCount = countValue(validity, 0xFF);
            long positiveBasePixelCount = Arrays.stream(baseClassIds)
                .filter(classId -> classId != BlockClassManifest.BACKGROUND_CLASS_ID)
                .count();
            // Unsupported emitters stay invalid instead of becoming indistinguishable from darkness.
            long unsupportedBasePixelCount = positiveBasePixelCount - validBasePixelCount;
            return new LightingReadback(
                rgb,
                validity,
                validBasePixelCount,
                unsupportedBasePixelCount
            );
        }
    }

    private static NativeImage readColor(Framebuffer framebuffer) {
        framebuffer.beginRead();
        try {
            NativeImage image = new NativeImage(WIDTH, HEIGHT, false);
            image.loadFromTextureImage(0, false);
            image.mirrorVertically();
            return image;
        } finally {
            framebuffer.endRead();
        }
    }

    private static float[] readDepth(Framebuffer framebuffer) {
        framebuffer.beginWrite(false);
        int previousPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        FloatBuffer bottomLeft = BufferUtils.createFloatBuffer(Math.multiplyExact(WIDTH, HEIGHT));
        try {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(
                0,
                0,
                WIDTH,
                HEIGHT,
                GL11.GL_DEPTH_COMPONENT,
                GL11.GL_FLOAT,
                bottomLeft
            );
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
        }

        float[] topLeft = new float[Math.multiplyExact(WIDTH, HEIGHT)];
        for (int y = 0; y < HEIGHT; y++) {
            int sourceRow = HEIGHT - 1 - y;
            for (int x = 0; x < WIDTH; x++) {
                topLeft[y * WIDTH + x] = bottomLeft.get(sourceRow * WIDTH + x);
            }
        }
        return topLeft;
    }

    private static Map<Integer, Long> observedClasses(
        TranslucentProbeLayer solidFluid,
        List<TranslucentProbeLayer> storedLayers
    ) {
        TreeMap<Integer, Long> observed = new TreeMap<>();
        List<TranslucentProbeLayer> layers = new ArrayList<>(storedLayers);
        layers.add(solidFluid);
        for (TranslucentProbeLayer layer : layers) {
            int pixels = Math.multiplyExact(layer.getWidth(), layer.getHeight());
            for (int pixel = 0; pixel < pixels; pixel++) {
                int classId = layer.getClassId(pixel);
                if (classId != BlockClassManifest.BACKGROUND_CLASS_ID) {
                    observed.put(classId, observed.getOrDefault(classId, 0L) + 1L);
                }
            }
        }
        return observed;
    }

    private static TranslucentProbeMetadata.Artifact artifact(
        String fileName,
        String encoding,
        int bytesPerPixel,
        byte[] data
    ) {
        return new TranslucentProbeMetadata.Artifact(
            fileName,
            encoding,
            bytesPerPixel,
            data.length
        );
    }

    private static void write(Path directory, String fileName, byte[] data) throws IOException {
        Files.write(directory.resolve(fileName), data);
    }

    private static void writeVisualization(Path path, int[] classIds) throws IOException {
        if (classIds.length != Math.multiplyExact(WIDTH, HEIGHT)) {
            throw new IllegalArgumentException("Visualization dimensions do not match");
        }
        try (NativeImage visualization = new NativeImage(WIDTH, HEIGHT, false)) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int classId = classIds[y * WIDTH + x];
                    visualization.setPixelColor(
                        x,
                        y,
                        toAbgr(SemanticClassEncoding.falseColorRgb(classId))
                    );
                }
            }
            visualization.writeFile(path);
        }
    }

    private static void writeGrayscaleVisualization(Path path, byte[] values) throws IOException {
        TranslucentProbeBufferEncoding.requireU8(WIDTH, HEIGHT, values);
        try (NativeImage visualization = new NativeImage(WIDTH, HEIGHT, false)) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int value = Byte.toUnsignedInt(values[y * WIDTH + x]);
                    visualization.setPixelColor(
                        x,
                        y,
                        0xFF000000 | value << 16 | value << 8 | value
                    );
                }
            }
            visualization.writeFile(path);
        }
    }

    private static void writeRgbVisualization(Path path, byte[] values) throws IOException {
        LightingRetentionBufferEncoding.requireRgb(WIDTH, HEIGHT, values);
        try (NativeImage visualization = new NativeImage(WIDTH, HEIGHT, false)) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    int offset = (y * WIDTH + x)
                        * LightingRetentionBufferEncoding.RGB_BYTES_PER_PIXEL;
                    int red = Byte.toUnsignedInt(values[offset]);
                    int green = Byte.toUnsignedInt(values[offset + 1]);
                    int blue = Byte.toUnsignedInt(values[offset + 2]);
                    visualization.setPixelColor(
                        x,
                        y,
                        0xFF000000 | blue << 16 | green << 8 | red
                    );
                }
            }
            visualization.writeFile(path);
        }
    }

    private static long countValue(byte[] values, int expected) {
        return Arrays.stream(toUnsignedInts(values)).filter(value -> value == expected).count();
    }

    private static int[] toUnsignedInts(byte[] values) {
        int[] unsigned = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            unsigned[index] = Byte.toUnsignedInt(values[index]);
        }
        return unsigned;
    }

    private static String captureStem() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return "probe-" + format.format(new Date()) + "-" + CAPTURE_SEQUENCE.incrementAndGet();
    }

    private static int toAbgr(int rgb) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        return 0xFF000000 | blue << 16 | green << 8 | red;
    }

    private enum ColorChannel {
        RED,
        ALPHA
    }

    public static final class PreparedCapture {
        // Existing F9 arrays stay in memory until this frame's camera-overlay pass ends.
        private final Path captureDirectory;
        private final String graphicsMode;
        private final String cameraMedium;
        private final Collection<String> resourcePacks;
        private final FogRetentionCaptureContext fogContext;
        private final LightingRetentionCaptureContext lightingContext;
        private final BlockClassVocabulary vocabulary;
        private final int[] baseClassIds;
        private final float[] baseDepth;
        private final byte[] baseLightingRetention;
        private final byte[] baseLightingValidity;
        private final long validLightingBasePixelCount;
        private final long unsupportedLightingBasePixelCount;
        private final byte[] baseFogRetention;
        private final TranslucentProbeLayer solidFluid;
        private final List<TranslucentProbeLayer> storedLayers;
        private final TranslucentProbeCandidates candidates;
        private final byte[] accumulatedOpacity;
        private final byte[] acceptedFragmentCount;
        private boolean completed;

        private PreparedCapture(
            Path captureDirectory,
            String graphicsMode,
            String cameraMedium,
            Collection<String> resourcePacks,
            FogRetentionCaptureContext fogContext,
            LightingRetentionCaptureContext lightingContext,
            BlockClassVocabulary vocabulary,
            int[] baseClassIds,
            float[] baseDepth,
            byte[] baseLightingRetention,
            byte[] baseLightingValidity,
            long validLightingBasePixelCount,
            long unsupportedLightingBasePixelCount,
            byte[] baseFogRetention,
            TranslucentProbeLayer solidFluid,
            List<TranslucentProbeLayer> storedLayers,
            TranslucentProbeCandidates candidates,
            byte[] accumulatedOpacity,
            byte[] acceptedFragmentCount
        ) {
            this.captureDirectory = captureDirectory;
            this.graphicsMode = graphicsMode;
            this.cameraMedium = cameraMedium;
            this.resourcePacks = new ArrayList<>(resourcePacks);
            this.fogContext = fogContext;
            this.lightingContext = lightingContext;
            this.vocabulary = vocabulary;
            this.baseClassIds = baseClassIds;
            this.baseDepth = baseDepth;
            this.baseLightingRetention = baseLightingRetention;
            this.baseLightingValidity = baseLightingValidity;
            this.validLightingBasePixelCount = validLightingBasePixelCount;
            this.unsupportedLightingBasePixelCount = unsupportedLightingBasePixelCount;
            this.baseFogRetention = baseFogRetention;
            this.solidFluid = solidFluid;
            this.storedLayers = new ArrayList<>(storedLayers);
            this.candidates = candidates;
            this.accumulatedOpacity = accumulatedOpacity;
            this.acceptedFragmentCount = acceptedFragmentCount;
        }

        public Path complete(CameraOverlayRetentionResult cameraOverlayRetention)
            throws IOException {
            if (completed) {
                throw new IllegalStateException("Prepared F9 capture was already completed");
            }
            completed = true;
            return writeCapture(
                captureDirectory,
                graphicsMode,
                cameraMedium,
                resourcePacks,
                fogContext,
                lightingContext,
                vocabulary,
                baseClassIds,
                baseDepth,
                baseLightingRetention,
                baseLightingValidity,
                validLightingBasePixelCount,
                unsupportedLightingBasePixelCount,
                baseFogRetention,
                solidFluid,
                storedLayers,
                candidates,
                accumulatedOpacity,
                acceptedFragmentCount,
                cameraOverlayRetention
            );
        }
    }

    private static final class LightingReadback {
        private final byte[] rgb;
        private final byte[] validity;
        private final long validBasePixelCount;
        private final long unsupportedBasePixelCount;

        private LightingReadback(
            byte[] rgb,
            byte[] validity,
            long validBasePixelCount,
            long unsupportedBasePixelCount
        ) {
            this.rgb = rgb;
            this.validity = validity;
            this.validBasePixelCount = validBasePixelCount;
            this.unsupportedBasePixelCount = unsupportedBasePixelCount;
        }
    }
}
