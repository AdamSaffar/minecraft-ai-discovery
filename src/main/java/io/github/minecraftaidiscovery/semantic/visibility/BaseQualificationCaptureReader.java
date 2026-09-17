package io.github.minecraftaidiscovery.semantic.visibility;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

public final class BaseQualificationCaptureReader {
    private static final String BASE_CLASSES = "base.classes.u16le";
    private static final String BASE_DEPTH = "base.depth.f32le";
    private static final String LIGHTING = "base.lighting-retention.rgb8";
    private static final String LIGHTING_VALIDITY = "base.lighting-validity.u8";
    private static final String FOG = "base.fog-retention.u8";
    private static final String OPACITY = "translucent.accumulated-opacity.u8";
    private static final String OVERLAY = "base.camera-overlay-retention.u8";
    private static final String FIRST_PERSON = "base.first-person-depth-coverage.u8";
    private static final String OVERFLOW = "translucent.storage-overflow.u8";
    private static final String RGB = "frame.pre-hud-rgb.rgb8";
    private static final String RGB_PNG = "frame-pre-hud-rgb.png";
    private static final String METADATA = "metadata.json";

    private static final String[] HASHED_INPUTS = {
        METADATA,
        BASE_CLASSES,
        BASE_DEPTH,
        LIGHTING,
        LIGHTING_VALIDITY,
        FOG,
        OPACITY,
        OVERLAY,
        FIRST_PERSON,
        OVERFLOW,
        RGB,
        RGB_PNG
    };

    private BaseQualificationCaptureReader() {
    }

    public static BaseQualificationCapture read(
        Path captureDirectory,
        String expectedManifestSha256
    ) throws IOException {
        if (captureDirectory == null) {
            throw new IllegalArgumentException("Calibration capture directory is missing");
        }
        Path directory = captureDirectory.toAbsolutePath().normalize();
        BaseInfluenceProbeCapture influence = BaseInfluenceProbeDirectoryReader.read(directory);
        JsonObject metadata = readMetadata(directory);
        requireCalibrationMetadata(metadata, expectedManifestSha256);

        int pixels = Math.multiplyExact(
            influence.getComposition().getWidth(),
            influence.getComposition().getHeight()
        );
        byte[] encodedClasses = readExact(directory, BASE_CLASSES, pixels * 2);
        byte[] encodedDepth = readExact(directory, BASE_DEPTH, pixels * 4);
        byte[] lighting = readExact(directory, LIGHTING, pixels * 3);
        byte[] lightingValidity = readExact(directory, LIGHTING_VALIDITY, pixels);
        byte[] fog = readExact(directory, FOG, pixels);
        byte[] opacity = readExact(directory, OPACITY, pixels);
        byte[] overlay = readExact(directory, OVERLAY, pixels);
        byte[] firstPerson = readExact(directory, FIRST_PERSON, pixels);
        byte[] overflow = readExact(directory, OVERFLOW, pixels);
        byte[] rgb = readExact(directory, RGB, pixels * 3);

        int[] classes = decodeInts(encodedClasses, pixels, 2);
        int[] depthBits = decodeInts(encodedDepth, pixels, 4);
        // Rounded composed bytes cannot prove an exact zero, so retain that fact from raw factors.
        byte[] exactZero = exactZeroMask(
            influence.getComposition(),
            lighting,
            lightingValidity,
            fog,
            opacity,
            overlay,
            firstPerson,
            overflow
        );
        requireRgbPng(directory.resolve(RGB_PNG), influence.getComposition(), rgb);

        Map<String, String> hashes = new LinkedHashMap<>();
        for (String fileName : HASHED_INPUTS) {
            Path file = directory.resolve(fileName);
            requireRegularFile(file, fileName);
            hashes.put(fileName, sha256(file));
        }
        return new BaseQualificationCapture(
            influence,
            classes,
            depthBits,
            exactZero,
            hashes
        );
    }

    private static byte[] exactZeroMask(
        BaseInfluenceCompositionResult composition,
        byte[] lighting,
        byte[] lightingValidity,
        byte[] fog,
        byte[] opacity,
        byte[] overlay,
        byte[] firstPerson,
        byte[] overflow
    ) {
        byte[] reasons = composition.copyReasons();
        byte[] mask = new byte[reasons.length];
        for (int pixel = 0; pixel < reasons.length; pixel++) {
            int reason = Byte.toUnsignedInt(reasons[pixel]);
            if (reason != BaseInfluenceReasonEncoding.SUPPORTED) {
                continue;
            }
            requireValue("lighting validity", lightingValidity[pixel], 255);
            requireValue("first-person coverage", firstPerson[pixel], 0);
            requireValue("translucent overflow", overflow[pixel], 0);
            int rgbOffset = pixel * 3;
            boolean noLighting = lighting[rgbOffset] == 0
                && lighting[rgbOffset + 1] == 0
                && lighting[rgbOffset + 2] == 0;
            boolean zeroCommonFactor = fog[pixel] == 0
                || Byte.toUnsignedInt(opacity[pixel]) == 255
                || overlay[pixel] == 0;
            if (noLighting || zeroCommonFactor) {
                mask[pixel] = 1;
            }
        }
        return mask;
    }

    private static void requireRgbPng(
        Path pngPath,
        BaseInfluenceCompositionResult composition,
        byte[] expectedRgb
    ) throws IOException {
        requireRegularFile(pngPath, RGB_PNG);
        BufferedImage image = ImageIO.read(pngPath.toFile());
        if (image == null
            || image.getWidth() != composition.getWidth()
            || image.getHeight() != composition.getHeight()) {
            throw new IllegalArgumentException("Same-frame RGB PNG dimensions are invalid");
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = y * image.getWidth() + x;
                int packed = image.getRGB(x, y);
                int offset = pixel * 3;
                if (((packed >>> 16) & 0xFF) != Byte.toUnsignedInt(expectedRgb[offset])
                    || ((packed >>> 8) & 0xFF) != Byte.toUnsignedInt(expectedRgb[offset + 1])
                    || (packed & 0xFF) != Byte.toUnsignedInt(expectedRgb[offset + 2])) {
                    throw new IllegalArgumentException(
                        "Same-frame RGB PNG differs from its raw buffer at pixel " + pixel
                    );
                }
            }
        }
    }

    private static JsonObject readMetadata(Path directory) throws IOException {
        Path metadataPath = directory.resolve(METADATA);
        requireRegularFile(metadataPath, METADATA);
        try (Reader reader = Files.newBufferedReader(metadataPath, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Calibration metadata root must be an object");
            }
            return parsed.getAsJsonObject();
        }
    }

    private static void requireCalibrationMetadata(
        JsonObject metadata,
        String expectedManifestSha256
    ) {
        if (expectedManifestSha256 == null || expectedManifestSha256.isEmpty()) {
            throw new IllegalArgumentException("Expected manifest fingerprint is missing");
        }
        JsonObject manifest = requireObject(metadata, "manifest");
        requireString(manifest, "sha256", expectedManifestSha256);
        JsonObject sameFrame = requireObject(metadata, "sameFrameRgb");
        requireString(
            sameFrame,
            "captureBoundary",
            SameFrameRgbCaptureBoundary.RENDER_HAND_RETURN.getMetadataName()
        );
        requireBoolean(sameFrame, "overlayStageEntered", true);
        requireBoolean(
            sameFrame,
            "firstPersonDepthCoverageOnPositiveBasePixels",
            false
        );
    }

    private static int[] decodeInts(byte[] encoded, int values, int bytesPerValue) {
        ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        int[] decoded = new int[values];
        for (int index = 0; index < values; index++) {
            decoded[index] = bytesPerValue == 2
                ? Short.toUnsignedInt(buffer.getShort())
                : buffer.getInt();
        }
        return decoded;
    }

    private static byte[] readExact(Path directory, String fileName, int expectedLength)
        throws IOException {
        Path path = directory.resolve(fileName).normalize();
        if (!directory.equals(path.getParent())) {
            throw new IllegalArgumentException("Calibration artifact leaves its capture directory");
        }
        requireRegularFile(path, fileName);
        if (Files.size(path) != expectedLength) {
            throw new IllegalArgumentException(
                "Calibration artifact has an unexpected length: " + fileName
            );
        }
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length != expectedLength) {
            throw new IllegalArgumentException(
                "Calibration artifact changed while it was read: " + fileName
            );
        }
        return bytes;
    }

    private static void requireRegularFile(Path path, String fileName) {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                "Calibration artifact is missing: " + fileName,
                exception
            );
        }
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new IllegalArgumentException(
                "Calibration artifact must be an unlinked regular file: " + fileName
            );
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            for (byte value : bytes) {
                digest.update(value);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) {
                hex.append(String.format(Locale.ROOT, "%02X", Byte.toUnsignedInt(value)));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireValue(String name, byte actual, int expected) {
        if (Byte.toUnsignedInt(actual) != expected) {
            throw new IllegalArgumentException(name + " conflicts with the composition reason");
        }
    }

    private static JsonObject requireObject(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("Calibration metadata object is missing: " + name);
        }
        return value.getAsJsonObject();
    }

    private static void requireString(JsonObject parent, String name, String expected) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
            || !expected.equals(value.getAsString())) {
            throw new IllegalArgumentException(
                "Calibration metadata " + name + " must be " + expected
            );
        }
    }

    private static void requireBoolean(JsonObject parent, String name, boolean expected) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()
            || value.getAsBoolean() != expected) {
            throw new IllegalArgumentException(
                "Calibration metadata " + name + " must be " + expected
            );
        }
    }
}
