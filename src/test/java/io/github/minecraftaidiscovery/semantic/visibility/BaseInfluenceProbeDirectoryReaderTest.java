package io.github.minecraftaidiscovery.semantic.visibility;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BaseInfluenceProbeDirectoryReaderTest {
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int PIXELS = WIDTH * HEIGHT;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readsVersionFiveArtifactsAndComposesTheirValues() throws IOException {
        Path capture = createCapture("probe-v5", "translucent-observation-probe-v5", 5);
        writePixel(capture, 0, 513, 200, 100, 50, 255, 128, 64, 0, 127, 0);
        writePixel(capture, 1, 8, 255, 255, 255, 0, 255, 0, 1, 255, 255);

        BaseInfluenceProbeCapture loaded = BaseInfluenceProbeDirectoryReader.read(capture);

        assertEquals("probe-v5", loaded.getCaptureName());
        assertArrayEquals(
            new byte[] {
                (byte)expectedProduct(200, 128, 191, 127),
                (byte)expectedProduct(100, 128, 191, 127),
                (byte)expectedProduct(50, 128, 191, 127)
            },
            firstRgb(loaded.getComposition().copyRgb())
        );
        assertEquals(
            BaseInfluenceReasonEncoding.SUPPORTED,
            Byte.toUnsignedInt(loaded.getComposition().copyReasons()[0])
        );
        assertEquals(
            BaseInfluenceReasonEncoding.UNSUPPORTED_LIGHTING
                | BaseInfluenceReasonEncoding.TRANSLUCENT_OVERFLOW
                | BaseInfluenceReasonEncoding.FIRST_PERSON_RETENTION_UNSUPPORTED,
            Byte.toUnsignedInt(loaded.getComposition().copyReasons()[1])
        );
    }

    @Test
    public void readsOnlyVersionFiveDirectoriesFromAMixedRoot() throws IOException {
        Path root = temporaryFolder.newFolder("probe-root").toPath();
        Path versionFive = root.resolve("probe-2");
        Path versionFour = root.resolve("probe-1");
        createCapture(versionFive, "translucent-observation-probe-v5", 5);
        createCapture(versionFour, "translucent-observation-probe-v4", 4);

        List<BaseInfluenceProbeCapture> captures =
            BaseInfluenceProbeDirectoryReader.readAllVersionFive(root);

        assertEquals(1, captures.size());
        assertEquals("probe-2", captures.get(0).getCaptureName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOlderFormatWhenReadDirectly() throws IOException {
        Path capture = createCapture("probe-v4", "translucent-observation-probe-v4", 4);
        BaseInfluenceProbeDirectoryReader.read(capture);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDeclaredArtifactLengthMismatch() throws IOException {
        Path capture = createCapture("wrong-length", "translucent-observation-probe-v5", 5);
        Path metadata = capture.resolve("metadata.json");
        String json = new String(Files.readAllBytes(metadata), StandardCharsets.UTF_8)
            .replace("\"byteLength\": 460800", "\"byteLength\": 2");
        Files.write(metadata, json.getBytes(StandardCharsets.UTF_8));
        BaseInfluenceProbeDirectoryReader.read(capture);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTruncatedArtifact() throws IOException {
        Path capture = createCapture("truncated", "translucent-observation-probe-v5", 5);
        Files.write(capture.resolve("base.fog-retention.u8"), new byte[PIXELS - 1]);
        BaseInfluenceProbeDirectoryReader.read(capture);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMetadataThatMarksTheProbeAgentVisible() throws IOException {
        Path capture = createCapture("agent-visible", "translucent-observation-probe-v5", 5);
        Path metadata = capture.resolve("metadata.json");
        String json = new String(Files.readAllBytes(metadata), StandardCharsets.UTF_8)
            .replace(
                "\"researcherOnly\": true, \"agentVisible\": false",
                "\"researcherOnly\": true, \"agentVisible\": true"
            );
        Files.write(metadata, json.getBytes(StandardCharsets.UTF_8));
        BaseInfluenceProbeDirectoryReader.read(capture);
    }

    @Test
    public void summarizesSupportedAndUnsupportedPixelsSeparately() {
        byte[] reasons = {
            0,
            1,
            2,
            4,
            8,
            (byte)(2 | 4 | 8)
        };
        byte[] rgb = {
            0, 0, 0,
            10, 20, 30,
            0, 0, 0,
            0, 0, 0,
            0, 0, 0,
            0, 0, 0
        };
        BaseInfluenceProbeSummary summary = BaseInfluenceProbeSummary.from(
            new BaseInfluenceProbeCapture(
                "summary",
                new BaseInfluenceCompositionResult(6, 1, rgb, reasons)
            )
        );

        assertEquals(1, summary.getBackgroundPixels());
        assertEquals(1, summary.getSupportedPixels());
        assertEquals(2, summary.getUnsupportedLightingPixels());
        assertEquals(2, summary.getOverflowPixels());
        assertEquals(2, summary.getFirstPersonUnsupportedPixels());
        assertEquals(10, summary.getMinimum(0));
        assertEquals(30, summary.getMaximum(2));
        assertEquals(20.0, summary.getMean(1), 0.0);
    }

    private Path createCapture(String name, String format, int formatVersion) throws IOException {
        return createCapture(temporaryFolder.newFolder(name).toPath(), format, formatVersion);
    }

    private static Path createCapture(Path capture, String format, int formatVersion)
        throws IOException {
        Files.createDirectories(capture);
        write(capture, "base.classes.u16le", new byte[PIXELS * 2]);
        write(capture, "base.lighting-retention.rgb8", new byte[PIXELS * 3]);
        write(capture, "base.lighting-validity.u8", new byte[PIXELS]);
        write(capture, "base.fog-retention.u8", new byte[PIXELS]);
        write(capture, "base.camera-overlay-retention.u8", new byte[PIXELS]);
        write(capture, "base.first-person-depth-coverage.u8", new byte[PIXELS]);
        write(capture, "translucent.accumulated-opacity.u8", new byte[PIXELS]);
        write(capture, "translucent.storage-overflow.u8", new byte[PIXELS]);
        Files.write(
            capture.resolve("metadata.json"),
            metadata(format, formatVersion).getBytes(StandardCharsets.UTF_8)
        );
        return capture;
    }

    private static void writePixel(
        Path capture,
        int pixel,
        int classId,
        int red,
        int green,
        int blue,
        int lightingValidity,
        int fog,
        int opacity,
        int overflow,
        int overlay,
        int firstPerson
    ) throws IOException {
        byte[] classes = Files.readAllBytes(capture.resolve("base.classes.u16le"));
        ByteBuffer.wrap(classes).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(pixel * 2, (short)classId);
        Files.write(capture.resolve("base.classes.u16le"), classes);
        writeValues(capture, "base.lighting-retention.rgb8", pixel * 3, red, green, blue);
        writeValues(capture, "base.lighting-validity.u8", pixel, lightingValidity);
        writeValues(capture, "base.fog-retention.u8", pixel, fog);
        writeValues(capture, "translucent.accumulated-opacity.u8", pixel, opacity);
        writeValues(capture, "translucent.storage-overflow.u8", pixel, overflow);
        writeValues(capture, "base.camera-overlay-retention.u8", pixel, overlay);
        writeValues(capture, "base.first-person-depth-coverage.u8", pixel, firstPerson);
    }

    private static void writeValues(Path capture, String fileName, int offset, int... values)
        throws IOException {
        Path path = capture.resolve(fileName);
        byte[] bytes = Files.readAllBytes(path);
        for (int index = 0; index < values.length; index++) {
            bytes[offset + index] = (byte)values[index];
        }
        Files.write(path, bytes);
    }

    private static void write(Path capture, String fileName, byte[] bytes) throws IOException {
        Files.write(capture.resolve(fileName), bytes);
    }

    private static byte[] firstRgb(byte[] rgb) {
        return new byte[] {rgb[0], rgb[1], rgb[2]};
    }

    private static int expectedProduct(int first, int second, int third, int fourth) {
        long denominator = 255L * 255L * 255L;
        long numerator = (long)first * second * third * fourth;
        return (int)((numerator + denominator / 2L) / denominator);
    }

    private static String metadata(String format, int formatVersion) {
        return "{\n"
            + "  \"format\": \"" + format + "\",\n"
            + "  \"formatVersion\": " + formatVersion + ",\n"
            + "  \"status\": {\"experimental\": true, \"researcherOnly\": true, "
            + "\"agentVisible\": false, \"productionObservationContract\": false},\n"
            + "  \"ordinarySemanticCapture\": {\"format\": \"semantic-observation-v3\", "
            + "\"unchanged\": true},\n"
            + "  \"minecraftVersion\": \"1.16.1\",\n"
            + "  \"yarnMappings\": \"1.16.1+build.21\",\n"
            + "  \"width\": 640,\n"
            + "  \"height\": 360,\n"
            + "  \"pixelOrder\": \"row-major, top-left origin\",\n"
            + "  \"graphicsMode\": \"fancy\",\n"
            + "  \"resourcePacks\": [\"vanilla\"],\n"
            + "  \"baseLightingRetention\": {\"status\": \"valid\", "
            + "\"thresholdApplied\": false, \"agentVisible\": false},\n"
            + "  \"baseCameraOverlayRetention\": {\"status\": \"valid\", "
            + "\"thresholdApplied\": false, \"agentVisible\": false},\n"
            + "  \"baseFirstPersonDepthCoverage\": {\"status\": \"valid\", "
            + "\"visibilityThresholdApplied\": false, \"agentVisible\": false, "
            + "\"opacityOrRetentionMeasurement\": false},\n"
            + "  \"temporaryStorage\": {\"capacity\": 4, \"productionK\": false},\n"
            + "  \"artifacts\": {\n"
            + artifact("baseClasses", "base.classes.u16le", 2)
            + artifact("baseLightingRetention", "base.lighting-retention.rgb8", 3)
            + artifact("baseLightingValidity", "base.lighting-validity.u8", 1)
            + artifact("baseFogRetention", "base.fog-retention.u8", 1)
            + artifact("baseCameraOverlayRetention", "base.camera-overlay-retention.u8", 1)
            + artifact(
                "baseFirstPersonDepthCoverage",
                "base.first-person-depth-coverage.u8",
                1
            )
            + artifact("accumulatedOpacity", "translucent.accumulated-opacity.u8", 1)
            + artifactLast("storageOverflow", "translucent.storage-overflow.u8", 1)
            + "  }\n"
            + "}\n";
    }

    private static String artifact(String key, String file, int bytesPerPixel) {
        return artifactValue(key, file, bytesPerPixel) + ",\n";
    }

    private static String artifactLast(String key, String file, int bytesPerPixel) {
        return artifactValue(key, file, bytesPerPixel) + "\n";
    }

    private static String artifactValue(String key, String file, int bytesPerPixel) {
        return "    \"" + key + "\": {\"file\": \"" + file
            + "\", \"bytesPerPixel\": " + bytesPerPixel
            + ", \"byteLength\": " + (PIXELS * bytesPerPixel) + "}";
    }
}
