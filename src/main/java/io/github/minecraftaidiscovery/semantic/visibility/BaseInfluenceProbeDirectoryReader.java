package io.github.minecraftaidiscovery.semantic.visibility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeCandidates;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeMetadata;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class BaseInfluenceProbeDirectoryReader {
    private static final int EXPECTED_WIDTH = 640;
    private static final int EXPECTED_HEIGHT = 360;
    private static final long MAX_METADATA_BYTES = 1024L * 1024L;

    private BaseInfluenceProbeDirectoryReader() {
    }

    public static BaseInfluenceProbeCapture read(Path captureDirectory) throws IOException {
        Path directory = requireDirectory(captureDirectory);
        JsonObject metadata = readMetadata(directory);
        requireMetadata(metadata);

        int width = requireInt(metadata, "width");
        int height = requireInt(metadata, "height");
        if (width != EXPECTED_WIDTH || height != EXPECTED_HEIGHT) {
            throw new IllegalArgumentException("Version-6 probe dimensions must be 640 x 360");
        }
        int pixels = BaseInfluenceComposition.requireDimensions(width, height);
        JsonObject artifacts = requireObject(metadata, "artifacts");

        byte[] encodedBaseClasses = readArtifact(
            directory,
            artifacts,
            "baseClasses",
            "base.classes.u16le",
            2,
            pixels
        );
        int[] baseClassIds = decodeBaseClasses(encodedBaseClasses, pixels);
        byte[] lightingRgb = readArtifact(
            directory,
            artifacts,
            "baseLightingRetention",
            "base.lighting-retention.rgb8",
            3,
            pixels
        );
        byte[] lightingValidity = readArtifact(
            directory,
            artifacts,
            "baseLightingValidity",
            "base.lighting-validity.u8",
            1,
            pixels
        );
        byte[] fogRetention = readArtifact(
            directory,
            artifacts,
            "baseFogRetention",
            "base.fog-retention.u8",
            1,
            pixels
        );
        byte[] cameraOverlayRetention = readArtifact(
            directory,
            artifacts,
            "baseCameraOverlayRetention",
            "base.camera-overlay-retention.u8",
            1,
            pixels
        );
        byte[] firstPersonDepthCoverage = readArtifact(
            directory,
            artifacts,
            "baseFirstPersonDepthCoverage",
            "base.first-person-depth-coverage.u8",
            1,
            pixels
        );
        byte[] accumulatedOpacity = readArtifact(
            directory,
            artifacts,
            "accumulatedOpacity",
            "translucent.accumulated-opacity.u8",
            1,
            pixels
        );
        byte[] translucentOverflow = readArtifact(
            directory,
            artifacts,
            "storageOverflow",
            "translucent.storage-overflow.u8",
            1,
            pixels
        );
        JsonObject sameFrameRgbArtifact = requireObject(artifacts, "framePreHudRgb");
        requireString(
            sameFrameRgbArtifact,
            "encoding",
            SameFrameRgbEncoding.ENCODING
        );
        byte[] sameFrameRgbBytes = readArtifact(
            directory,
            artifacts,
            "framePreHudRgb",
            "frame.pre-hud-rgb.rgb8",
            SameFrameRgbEncoding.RGB_BYTES_PER_PIXEL,
            pixels
        );
        SameFrameRgbResult sameFrameRgb =
            SameFrameRgbEncoding.fromTopToBottomRgb(sameFrameRgbBytes);

        BaseInfluenceCompositionResult composition = BaseInfluenceComposition.compose(
            width,
            height,
            baseClassIds,
            lightingRgb,
            lightingValidity,
            fogRetention,
            accumulatedOpacity,
            translucentOverflow,
            cameraOverlayRetention,
            firstPersonDepthCoverage
        );
        return new BaseInfluenceProbeCapture(
            directory.getFileName().toString(),
            composition,
            sameFrameRgb
        );
    }

    public static List<BaseInfluenceProbeCapture> readAllVersionSix(Path probeRoot)
        throws IOException {
        Path root = requireDirectory(probeRoot);
        List<Path> directories;
        try (Stream<Path> entries = Files.list(root)) {
            directories = entries
                .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .collect(Collectors.toList());
        }

        List<BaseInfluenceProbeCapture> captures = new ArrayList<>();
        for (Path directory : directories) {
            Path metadataPath = directory.resolve("metadata.json");
            if (!Files.isRegularFile(metadataPath, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            JsonObject metadata = readMetadata(directory);
            if (TranslucentProbeMetadata.FORMAT_NAME.equals(optionalString(metadata, "format"))) {
                captures.add(read(directory));
            }
        }
        return captures;
    }

    private static void requireMetadata(JsonObject metadata) {
        requireString(metadata, "format", TranslucentProbeMetadata.FORMAT_NAME);
        requireInt(metadata, "formatVersion", TranslucentProbeMetadata.FORMAT_VERSION);
        requireString(metadata, "minecraftVersion", "1.16.1");
        requireString(metadata, "yarnMappings", "1.16.1+build.21");
        requireString(metadata, "pixelOrder", "row-major, top-left origin");
        requireOneOf(metadata, "graphicsMode", "fast", "fancy");

        JsonObject status = requireObject(metadata, "status");
        requireBoolean(status, "experimental", true);
        requireBoolean(status, "researcherOnly", true);
        requireBoolean(status, "agentVisible", false);
        requireBoolean(status, "productionObservationContract", false);

        JsonObject ordinaryCapture = requireObject(metadata, "ordinarySemanticCapture");
        requireString(ordinaryCapture, "format", "semantic-observation-v3");
        requireBoolean(ordinaryCapture, "unchanged", true);

        JsonArray resourcePacks = requireArray(metadata, "resourcePacks");
        if (resourcePacks.size() != 1
            || !resourcePacks.get(0).isJsonPrimitive()
            || !resourcePacks.get(0).getAsJsonPrimitive().isString()
            || !"vanilla".equals(resourcePacks.get(0).getAsString())) {
            throw new IllegalArgumentException("Base-influence study requires vanilla resources");
        }

        JsonObject lighting = requireObject(metadata, "baseLightingRetention");
        requireOneOf(lighting, "status", "valid", "partial");
        requireBoolean(lighting, "thresholdApplied", false);
        requireBoolean(lighting, "agentVisible", false);

        JsonObject overlay = requireObject(metadata, "baseCameraOverlayRetention");
        requireString(overlay, "status", "valid");
        requireBoolean(overlay, "thresholdApplied", false);
        requireBoolean(overlay, "agentVisible", false);

        JsonObject firstPerson = requireObject(metadata, "baseFirstPersonDepthCoverage");
        requireString(firstPerson, "status", "valid");
        requireBoolean(firstPerson, "visibilityThresholdApplied", false);
        requireBoolean(firstPerson, "agentVisible", false);
        requireBoolean(firstPerson, "opacityOrRetentionMeasurement", false);

        JsonObject sameFrameRgb = requireObject(metadata, "sameFrameRgb");
        requireString(sameFrameRgb, "status", "valid");
        String captureBoundary = requireOneOf(
            sameFrameRgb,
            "captureBoundary",
            SameFrameRgbCaptureBoundary.RENDER_HAND_RETURN.getMetadataName(),
            SameFrameRgbCaptureBoundary.RENDER_WORLD_RETURN_WITHOUT_HAND.getMetadataName()
        );
        requireBoolean(
            sameFrameRgb,
            "handAndCameraOverlayStageComplete",
            SameFrameRgbCaptureBoundary.RENDER_HAND_RETURN.getMetadataName()
                .equals(captureBoundary)
        );
        requireBooleanValue(sameFrameRgb, "overlayStageEntered");
        requireBooleanValue(
            sameFrameRgb,
            "firstPersonDepthCoverageOnPositiveBasePixels"
        );
        requireInt(sameFrameRgb, "sourceWidth", SameFrameRgbEncoding.SOURCE_WIDTH);
        requireInt(sameFrameRgb, "sourceHeight", SameFrameRgbEncoding.SOURCE_HEIGHT);
        requireInt(sameFrameRgb, "width", SameFrameRgbEncoding.WIDTH);
        requireInt(sameFrameRgb, "height", SameFrameRgbEncoding.HEIGHT);
        requireInt(
            sameFrameRgb,
            "byteLength",
            SameFrameRgbEncoding.expectedRgbLength()
        );
        requireString(sameFrameRgb, "encoding", SameFrameRgbEncoding.ENCODING);
        requireString(sameFrameRgb, "pixelOrder", "row-major, top-left origin");
        requireString(sameFrameRgb, "channelOrder", "RGB");
        requireInt(
            sameFrameRgb,
            "downsampleFactor",
            SameFrameRgbEncoding.DOWNSAMPLE_FACTOR
        );
        requireBoolean(sameFrameRgb, "sameFrameAsProbeBuffers", true);
        requireBoolean(sameFrameRgb, "preHud", true);
        requireBoolean(sameFrameRgb, "preMenu", true);
        requireBoolean(sameFrameRgb, "prePostProcess", true);
        requireBoolean(sameFrameRgb, "additionalRenderPass", false);
        requireBoolean(sameFrameRgb, "additionalPlayerOrWorldQueries", false);
        requireBoolean(sameFrameRgb, "rawNativeResolutionSerialized", false);
        requireBoolean(sameFrameRgb, "thresholdApplied", false);
        requireBoolean(sameFrameRgb, "agentVisible", false);
        requireBoolean(sameFrameRgb, "perceptualVisibilityScore", false);

        JsonObject storage = requireObject(metadata, "temporaryStorage");
        requireInt(storage, "capacity", TranslucentProbeCandidates.TEMPORARY_STORAGE_CAPACITY);
        requireBoolean(storage, "productionK", false);
    }

    private static JsonObject readMetadata(Path directory) throws IOException {
        Path metadataPath = directory.resolve("metadata.json");
        requireRegularFile(metadataPath, "metadata.json");
        long size = Files.size(metadataPath);
        if (size <= 0 || size > MAX_METADATA_BYTES) {
            throw new IllegalArgumentException("Probe metadata size is invalid");
        }
        try (Reader reader = Files.newBufferedReader(metadataPath, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Probe metadata root must be an object");
            }
            return parsed.getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Probe metadata is invalid JSON", exception);
        }
    }

    private static byte[] readArtifact(
        Path directory,
        JsonObject artifacts,
        String artifactKey,
        String expectedFileName,
        int bytesPerPixel,
        int pixels
    ) throws IOException {
        JsonObject artifact = requireObject(artifacts, artifactKey);
        requireString(artifact, "file", expectedFileName);
        requireInt(artifact, "bytesPerPixel", bytesPerPixel);
        int expectedLength = Math.multiplyExact(pixels, bytesPerPixel);
        requireInt(artifact, "byteLength", expectedLength);

        Path artifactPath = directory.resolve(expectedFileName).normalize();
        if (!artifactPath.getParent().equals(directory)) {
            throw new IllegalArgumentException("Artifact path leaves the probe directory");
        }
        requireRegularFile(artifactPath, expectedFileName);
        if (Files.size(artifactPath) != expectedLength) {
            throw new IllegalArgumentException(
                "Artifact " + expectedFileName + " has an unexpected length"
            );
        }
        byte[] bytes = Files.readAllBytes(artifactPath);
        if (bytes.length != expectedLength) {
            throw new IllegalArgumentException(
                "Artifact " + expectedFileName + " changed while it was read"
            );
        }
        return bytes;
    }

    private static int[] decodeBaseClasses(byte[] encoded, int pixels) {
        ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        int[] classIds = new int[pixels];
        for (int pixel = 0; pixel < pixels; pixel++) {
            classIds[pixel] = Short.toUnsignedInt(buffer.getShort());
        }
        return classIds;
    }

    private static Path requireDirectory(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Probe directory is missing");
        }
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Probe directory does not exist: " + normalized);
        }
        return normalized;
    }

    private static void requireRegularFile(Path path, String name) {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException("Probe artifact is missing: " + name, exception);
        }
        requireSafeArtifactType(
            name,
            attributes.isRegularFile(),
            attributes.isSymbolicLink()
        );
    }

    static void requireSafeArtifactType(
        String name,
        boolean regularFile,
        boolean symbolicLink
    ) {
        if (!regularFile || symbolicLink) {
            throw new IllegalArgumentException(
                "Probe artifact must be an unlinked regular file: " + name
            );
        }
    }

    private static JsonObject requireObject(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("Probe metadata object is missing: " + name);
        }
        return value.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("Probe metadata array is missing: " + name);
        }
        return value.getAsJsonArray();
    }

    private static void requireString(JsonObject parent, String name, String expected) {
        String actual = optionalString(parent, name);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                "Probe metadata " + name + " must be " + expected
            );
        }
    }

    private static String optionalString(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        return value.getAsString();
    }

    private static int requireInt(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Probe metadata integer is missing: " + name);
        }
        try {
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Probe metadata integer is invalid: " + name, exception);
        }
    }

    private static void requireInt(JsonObject parent, String name, int expected) {
        if (requireInt(parent, name) != expected) {
            throw new IllegalArgumentException(
                "Probe metadata " + name + " must be " + expected
            );
        }
    }

    private static void requireBoolean(JsonObject parent, String name, boolean expected) {
        boolean actual = requireBooleanValue(parent, name);
        if (actual != expected) {
            throw new IllegalArgumentException(
                "Probe metadata " + name + " must be " + expected
            );
        }
    }

    private static boolean requireBooleanValue(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Probe metadata boolean is missing: " + name);
        }
        return value.getAsBoolean();
    }

    private static String requireOneOf(
        JsonObject parent,
        String name,
        String... expectedValues
    ) {
        String actual = optionalString(parent, name);
        for (String expected : expectedValues) {
            if (expected.equals(actual)) {
                return actual;
            }
        }
        throw new IllegalArgumentException("Probe metadata " + name + " is unsupported: " + actual);
    }
}
