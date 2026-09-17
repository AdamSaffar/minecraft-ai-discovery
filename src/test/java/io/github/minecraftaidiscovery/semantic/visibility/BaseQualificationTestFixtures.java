package io.github.minecraftaidiscovery.semantic.visibility;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class BaseQualificationTestFixtures {
    private static final int WIDTH = SameFrameRgbEncoding.WIDTH;
    private static final int HEIGHT = SameFrameRgbEncoding.HEIGHT;
    private static final int[] CLASS_IDS = {
        101, 102, 201, 202, 301, 302, 401, 402, 501, 502, 601, 602
    };

    private BaseQualificationTestFixtures() {
    }

    static Fixture create(Path directory) throws IOException {
        Path manifestPath = directory.resolve("study.json");
        JsonObject manifestJson = manifestJson();
        Files.write(
            manifestPath,
            (new GsonBuilder().setPrettyPrinting().create().toJson(manifestJson) + "\n")
                .getBytes(StandardCharsets.UTF_8)
        );
        BaseQualificationStudyManifest manifest = BaseQualificationStudyManifest.read(manifestPath);
        Map<String, BaseQualificationCapture> captures = new LinkedHashMap<>();
        for (BaseQualificationStudyManifest.Capture capture : manifest.getCaptures()) {
            captures.put(
                capture.getCaptureName(),
                syntheticCapture(
                    capture.getCaptureName(),
                    capture.getRole() == BaseQualificationStudyManifest.CaptureRole.BACKGROUND,
                    capture.getCaptureName().equals(manifest.getInsideBlockCapture())
                )
            );
        }
        return new Fixture(
            manifestPath,
            manifest,
            captures,
            BaseQualificationTrialPlan.create(manifest, captures)
        );
    }

    static BaseQualificationCapture smallComponentCapture(
        int componentWidth,
        int componentHeight,
        int influence,
        boolean exactZero
    ) {
        int pixels = WIDTH * HEIGHT;
        int[] classes = new int[pixels];
        int[] depth = new int[pixels];
        byte[] reasons = new byte[pixels];
        byte[] zero = new byte[pixels];
        byte[] composed = new byte[pixels * 3];
        byte[] frame = new byte[pixels * 3];
        int depthValue = Float.floatToIntBits(0.75f);
        for (int y = 100; y < 100 + componentHeight; y++) {
            for (int x = 100; x < 100 + componentWidth; x++) {
                int pixel = y * WIDTH + x;
                classes[pixel] = 7;
                depth[pixel] = depthValue;
                reasons[pixel] = BaseInfluenceReasonEncoding.SUPPORTED;
                zero[pixel] = exactZero ? (byte)1 : 0;
                int offset = pixel * 3;
                composed[offset] = (byte)influence;
                composed[offset + 1] = (byte)influence;
                composed[offset + 2] = (byte)influence;
            }
        }
        return capture("component", classes, depth, reasons, zero, composed, frame);
    }

    static Path writeResponses(
        Path path,
        BaseQualificationTrialPlan plan,
        BaseQualificationStudyManifest.Partition partition
    ) throws IOException {
        StringBuilder tsv = new StringBuilder("trial_id\tresponse\n");
        for (BaseQualificationPresentation presentation : plan.getPresentations()) {
            if (presentation.getTarget().getPair().getPartition() != partition) {
                continue;
            }
            BaseQualificationPresentation.Response response =
                presentation.getTarget().getControl()
                    == BaseQualificationTarget.Control.NEGATIVE_BLINDING
                    ? BaseQualificationPresentation.Response.UNCERTAIN
                    : presentation.getCorrectResponse();
            tsv.append(presentation.getTrialId())
                .append('\t')
                .append(response.name().toLowerCase(java.util.Locale.ROOT))
                .append('\n');
        }
        Files.write(path, tsv.toString().getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private static BaseQualificationCapture syntheticCapture(
        String name,
        boolean background,
        boolean exactZero
    ) {
        int pixels = WIDTH * HEIGHT;
        int[] classes = new int[pixels];
        int[] depth = new int[pixels];
        byte[] reasons = new byte[pixels];
        byte[] zero = new byte[pixels];
        byte[] composed = new byte[pixels * 3];
        byte[] frame = new byte[pixels * 3];
        if (!background) {
            int targetDepth = Float.floatToIntBits(0.8f);
            for (int pair = 0; pair < 6; pair++) {
                int top = 60 + pair * 12;
                int classA = CLASS_IDS[pair * 2];
                int classB = CLASS_IDS[pair * 2 + 1];
                for (int y = top; y < top + 8; y++) {
                    for (int x = 180; x < 460; x++) {
                        int classId = x < 320 ? classA : classB;
                        if (y >= top + 2 && y < top + 6 && x >= 220 && x < 232) {
                            classId = classB;
                        } else if (y >= top + 2 && y < top + 6 && x >= 408 && x < 420) {
                            classId = classA;
                        }
                        int pixel = y * WIDTH + x;
                        classes[pixel] = classId;
                        depth[pixel] = targetDepth;
                        reasons[pixel] = BaseInfluenceReasonEncoding.SUPPORTED;
                        zero[pixel] = exactZero ? (byte)1 : 0;
                        int offset = pixel * 3;
                        composed[offset] = 100;
                        composed[offset + 1] = 100;
                        composed[offset + 2] = 100;
                        frame[offset] = (byte)(classId & 0xFF);
                        frame[offset + 1] = (byte)((classId * 3) & 0xFF);
                        frame[offset + 2] = (byte)((classId * 7) & 0xFF);
                    }
                }
            }
        }
        return capture(name, classes, depth, reasons, zero, composed, frame);
    }

    private static BaseQualificationCapture capture(
        String name,
        int[] classes,
        int[] depth,
        byte[] reasons,
        byte[] zero,
        byte[] composed,
        byte[] frame
    ) {
        BaseInfluenceCompositionResult composition = new BaseInfluenceCompositionResult(
            WIDTH,
            HEIGHT,
            composed,
            reasons
        );
        SameFrameRgbResult rgb = new SameFrameRgbResult(
            SameFrameRgbEncoding.SOURCE_WIDTH,
            SameFrameRgbEncoding.SOURCE_HEIGHT,
            WIDTH,
            HEIGHT,
            frame
        );
        BaseInfluenceProbeCapture influence = new BaseInfluenceProbeCapture(
            name,
            composition,
            rgb
        );
        return new BaseQualificationCapture(
            influence,
            classes,
            depth,
            zero,
            Collections.singletonMap("synthetic", "HASH")
        );
    }

    private static JsonObject manifestJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", BaseQualificationStudyManifest.FORMAT);
        root.addProperty("randomSeed", "test-seed");
        root.addProperty("blockClassManifestSha256", "HASH");
        root.addProperty("referenceNearCapture", "C01-near");
        root.addProperty("referenceFarCapture", "C01-far");
        root.addProperty("insideBlockCapture", "c11-near");
        root.addProperty("skyCapture", "c12-sky");
        JsonArray pairs = new JsonArray();
        for (int pair = 0; pair < 6; pair++) {
            JsonObject value = new JsonObject();
            value.addProperty("code", pair < 3 ? "D" + (pair + 1) : "H" + (pair - 2));
            value.addProperty("partition", pair < 3 ? "development" : "held_out");
            value.addProperty("classA", CLASS_IDS[pair * 2]);
            value.addProperty("identityA", "test:a" + pair);
            value.addProperty("classB", CLASS_IDS[pair * 2 + 1]);
            value.addProperty("identityB", "test:b" + pair);
            pairs.add(value);
        }
        root.add("pairs", pairs);
        JsonArray captures = new JsonArray();
        for (int condition = 1; condition <= 9; condition++) {
            addCapture(captures, code(condition) + "-near", code(condition), condition - 1,
                "near", 0, 0, "target", condition == 4 ? "block-light" : null);
            addCapture(captures, code(condition) + "-far", code(condition), condition - 1,
                "far", 1, 0, "target", null);
            if (condition == 4) {
                for (int repeat = 1; repeat <= 4; repeat++) {
                    addCapture(captures, "c04-repeat-" + repeat, "C04", 3,
                        "near", 0, repeat, "temporal", "block-light");
                }
            }
        }
        addCapture(captures, "c10-near", "C10", 9, "near", 0, 0, "target", "fire");
        for (int repeat = 1; repeat <= 4; repeat++) {
            addCapture(captures, "c10-repeat-" + repeat, "C10", 9,
                "near", 0, repeat, "temporal", "fire");
        }
        addCapture(captures, "c11-near", "C11", 10, "near", 0, 0, "target", null);
        addCapture(captures, "c12-sky", "C12", 11, "background", 0, 0, "background", null);
        addCapture(captures, "c13-near", "C13", 12, "near", 0, 0, "target", null);
        addCapture(captures, "c14-near", "C14", 13, "near", 0, 0, "target", null);
        root.add("captures", captures);
        return root;
    }

    private static void addCapture(
        JsonArray captures,
        String name,
        String condition,
        int conditionOrdinal,
        String layout,
        int cameraOrdinal,
        int repeatIndex,
        String role,
        String temporalGroup
    ) {
        JsonObject capture = new JsonObject();
        capture.addProperty("capture", name);
        capture.addProperty("condition", condition);
        capture.addProperty("conditionOrdinal", conditionOrdinal);
        capture.addProperty("layout", layout);
        capture.addProperty("cameraOrdinal", cameraOrdinal);
        capture.addProperty("repeatIndex", repeatIndex);
        capture.addProperty("role", role);
        if (temporalGroup != null) {
            capture.addProperty("temporalGroup", temporalGroup);
        }
        captures.add(capture);
    }

    private static String code(int condition) {
        return String.format(java.util.Locale.ROOT, "C%02d", condition);
    }

    static final class Fixture {
        final Path manifestPath;
        final BaseQualificationStudyManifest manifest;
        final Map<String, BaseQualificationCapture> captures;
        final BaseQualificationTrialPlan plan;

        private Fixture(
            Path manifestPath,
            BaseQualificationStudyManifest manifest,
            Map<String, BaseQualificationCapture> captures,
            BaseQualificationTrialPlan plan
        ) {
            this.manifestPath = manifestPath;
            this.manifest = manifest;
            this.captures = captures;
            this.plan = plan;
        }
    }
}
