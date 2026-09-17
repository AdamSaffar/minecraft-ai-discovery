package io.github.minecraftaidiscovery.semantic.visibility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BaseQualificationStudyManifest {
    public static final String FORMAT = "base-identity-qualification-study-v1";
    public static final int EXPECTED_PAIR_COUNT = 6;
    public static final int EXPECTED_CAPTURE_COUNT = 31;
    public static final int EXPECTED_TARGET_FRAME_COUNT = 22;
    public static final int EXPECTED_TEMPORAL_FRAME_COUNT = 8;

    private final String randomSeed;
    private final String manifestSha256;
    private final String referenceNearCapture;
    private final String referenceFarCapture;
    private final String insideBlockCapture;
    private final String skyCapture;
    private final List<Pair> pairs;
    private final List<Capture> captures;

    private BaseQualificationStudyManifest(
        String randomSeed,
        String manifestSha256,
        String referenceNearCapture,
        String referenceFarCapture,
        String insideBlockCapture,
        String skyCapture,
        List<Pair> pairs,
        List<Capture> captures
    ) {
        this.randomSeed = randomSeed;
        this.manifestSha256 = manifestSha256;
        this.referenceNearCapture = referenceNearCapture;
        this.referenceFarCapture = referenceFarCapture;
        this.insideBlockCapture = insideBlockCapture;
        this.skyCapture = skyCapture;
        this.pairs = Collections.unmodifiableList(pairs);
        this.captures = Collections.unmodifiableList(captures);
        validate();
    }

    public static BaseQualificationStudyManifest read(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Calibration study manifest is missing");
        }
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Calibration study manifest must be an object");
            }
            root = parsed.getAsJsonObject();
        }
        requireString(root, "format", FORMAT);
        List<Pair> pairs = readPairs(requireArray(root, "pairs"));
        List<Capture> captures = readCaptures(requireArray(root, "captures"));
        return new BaseQualificationStudyManifest(
            requireString(root, "randomSeed"),
            requireString(root, "blockClassManifestSha256"),
            requireString(root, "referenceNearCapture"),
            requireString(root, "referenceFarCapture"),
            requireString(root, "insideBlockCapture"),
            requireString(root, "skyCapture"),
            pairs,
            captures
        );
    }

    public String getRandomSeed() {
        return randomSeed;
    }

    public String getManifestSha256() {
        return manifestSha256;
    }

    public String getReferenceNearCapture() {
        return referenceNearCapture;
    }

    public String getReferenceFarCapture() {
        return referenceFarCapture;
    }

    public String getInsideBlockCapture() {
        return insideBlockCapture;
    }

    public String getSkyCapture() {
        return skyCapture;
    }

    public List<Pair> getPairs() {
        return pairs;
    }

    public List<Capture> getCaptures() {
        return captures;
    }

    private void validate() {
        if (randomSeed.isEmpty() || manifestSha256.isEmpty()) {
            throw new IllegalArgumentException("Calibration seed or manifest hash is missing");
        }
        if (pairs.size() != EXPECTED_PAIR_COUNT) {
            throw new IllegalArgumentException("Calibration requires exactly six block pairs");
        }
        Set<String> pairCodes = new HashSet<>();
        Set<Integer> classIds = new HashSet<>();
        int development = 0;
        int heldOut = 0;
        for (Pair pair : pairs) {
            if (!pairCodes.add(pair.code)) {
                throw new IllegalArgumentException("Duplicate calibration pair code: " + pair.code);
            }
            if (!classIds.add(pair.classA) || !classIds.add(pair.classB)) {
                throw new IllegalArgumentException("A semantic class appears in multiple pairs");
            }
            if (pair.partition == Partition.DEVELOPMENT) {
                development++;
            } else {
                heldOut++;
            }
        }
        if (development != 3 || heldOut != 3) {
            throw new IllegalArgumentException("Calibration requires three development and three held-out pairs");
        }
        if (captures.size() != EXPECTED_CAPTURE_COUNT) {
            throw new IllegalArgumentException("Calibration requires exactly 31 accepted captures");
        }
        Set<String> captureNames = new HashSet<>();
        Map<String, Capture> captureByName = new HashMap<>();
        int targetFrames = 0;
        int temporalFrames = 0;
        int backgroundFrames = 0;
        for (Capture capture : captures) {
            if (!captureNames.add(capture.captureName)) {
                throw new IllegalArgumentException(
                    "Duplicate accepted capture: " + capture.captureName
                );
            }
            captureByName.put(capture.captureName, capture);
            if (capture.role == CaptureRole.TARGET) {
                targetFrames++;
            } else if (capture.role == CaptureRole.TEMPORAL) {
                temporalFrames++;
            } else {
                backgroundFrames++;
            }
        }
        if (targetFrames != EXPECTED_TARGET_FRAME_COUNT
            || temporalFrames != EXPECTED_TEMPORAL_FRAME_COUNT
            || backgroundFrames != 1) {
            throw new IllegalArgumentException("Calibration capture roles do not match the frozen matrix");
        }
        requireCapture(captureNames, referenceNearCapture, "near reference");
        requireCapture(captureNames, referenceFarCapture, "far reference");
        requireCapture(captureNames, insideBlockCapture, "inside-block control");
        requireCapture(captureNames, skyCapture, "sky control");
        requireCaptureRole(captureByName.get(referenceNearCapture), "C01", Layout.NEAR,
            CaptureRole.TARGET, "near reference");
        requireCaptureRole(captureByName.get(referenceFarCapture), "C01", Layout.FAR,
            CaptureRole.TARGET, "far reference");
        requireCaptureRole(captureByName.get(insideBlockCapture), "C11", Layout.NEAR,
            CaptureRole.TARGET, "inside-block control");
        requireCaptureRole(captureByName.get(skyCapture), "C12", Layout.BACKGROUND,
            CaptureRole.BACKGROUND, "sky control");
        validateCaptureMatrix();
    }

    private static void requireCapture(Set<String> names, String capture, String role) {
        if (!names.contains(capture)) {
            throw new IllegalArgumentException("Calibration " + role + " is not accepted: " + capture);
        }
    }

    private static void requireCaptureRole(
        Capture capture,
        String condition,
        Layout layout,
        CaptureRole role,
        String description
    ) {
        if (capture == null || !condition.equals(capture.condition)
            || capture.layout != layout || capture.role != role) {
            throw new IllegalArgumentException(
                "Calibration " + description + " has the wrong matrix role"
            );
        }
    }

    private void validateCaptureMatrix() {
        // These counts are the frozen protocol, not a flexible capture-discovery convention.
        for (int ordinal = 0; ordinal < 14; ordinal++) {
            String condition = String.format(java.util.Locale.ROOT, "C%02d", ordinal + 1);
            int expectedTargets = ordinal < 9 ? 2 : (ordinal == 11 ? 0 : 1);
            int actualTargets = 0;
            int actualTemporal = 0;
            int actualBackground = 0;
            for (Capture capture : captures) {
                if (!condition.equals(capture.condition)) {
                    continue;
                }
                if (capture.role == CaptureRole.TARGET) {
                    actualTargets++;
                } else if (capture.role == CaptureRole.TEMPORAL) {
                    actualTemporal++;
                } else {
                    actualBackground++;
                }
            }
            int expectedTemporal = ordinal == 3 || ordinal == 9 ? 4 : 0;
            int expectedBackground = ordinal == 11 ? 1 : 0;
            if (actualTargets != expectedTargets
                || actualTemporal != expectedTemporal
                || actualBackground != expectedBackground) {
                throw new IllegalArgumentException(
                    "Calibration captures do not match the frozen matrix for " + condition
                );
            }
        }
        requireTemporalGroup("block-light", "C04");
        requireTemporalGroup("fire", "C10");
    }

    private void requireTemporalGroup(String group, String condition) {
        int targets = 0;
        int repeats = 0;
        Set<Integer> repeatIndices = new HashSet<>();
        for (Capture capture : captures) {
            if (!group.equals(capture.temporalGroup)) {
                continue;
            }
            if (!condition.equals(capture.condition) || capture.layout != Layout.NEAR) {
                throw new IllegalArgumentException("Calibration temporal group has the wrong state");
            }
            if (capture.role == CaptureRole.TARGET) {
                targets++;
            } else if (capture.role == CaptureRole.TEMPORAL) {
                repeats++;
                repeatIndices.add(capture.repeatIndex);
            }
        }
        if (targets != 1 || repeats != 4 || repeatIndices.size() != 4
            || !repeatIndices.contains(1) || !repeatIndices.contains(2)
            || !repeatIndices.contains(3) || !repeatIndices.contains(4)) {
            throw new IllegalArgumentException("Calibration temporal group is incomplete: " + group);
        }
    }

    private static List<Pair> readPairs(JsonArray values) {
        List<Pair> pairs = new ArrayList<>();
        for (JsonElement value : values) {
            if (!value.isJsonObject()) {
                throw new IllegalArgumentException("Calibration pair must be an object");
            }
            JsonObject pair = value.getAsJsonObject();
            pairs.add(new Pair(
                requireString(pair, "code"),
                Partition.from(requireString(pair, "partition")),
                requireInt(pair, "classA"),
                requireString(pair, "identityA"),
                requireInt(pair, "classB"),
                requireString(pair, "identityB")
            ));
        }
        return pairs;
    }

    private static List<Capture> readCaptures(JsonArray values) {
        List<Capture> captures = new ArrayList<>();
        for (JsonElement value : values) {
            if (!value.isJsonObject()) {
                throw new IllegalArgumentException("Calibration capture must be an object");
            }
            JsonObject capture = value.getAsJsonObject();
            captures.add(new Capture(
                requireString(capture, "capture"),
                requireString(capture, "condition"),
                requireInt(capture, "conditionOrdinal"),
                Layout.from(requireString(capture, "layout")),
                requireInt(capture, "cameraOrdinal"),
                requireInt(capture, "repeatIndex"),
                CaptureRole.from(requireString(capture, "role")),
                optionalString(capture, "temporalGroup")
            ));
        }
        return captures;
    }

    private static JsonArray requireArray(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("Calibration manifest array is missing: " + name);
        }
        return value.getAsJsonArray();
    }

    private static String requireString(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
            || value.getAsString().isEmpty()) {
            throw new IllegalArgumentException("Calibration manifest string is missing: " + name);
        }
        return value.getAsString();
    }

    private static void requireString(JsonObject parent, String name, String expected) {
        String actual = requireString(parent, name);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Calibration manifest " + name + " must be " + expected);
        }
    }

    private static String optionalString(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
            || value.getAsString().isEmpty()) {
            throw new IllegalArgumentException("Calibration manifest string is invalid: " + name);
        }
        return value.getAsString();
    }

    private static int requireInt(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Calibration manifest integer is missing: " + name);
        }
        try {
            int integer = value.getAsInt();
            if (!Integer.toString(integer).equals(value.getAsString())) {
                throw new IllegalArgumentException(
                    "Calibration manifest integer is not exact: " + name
                );
            }
            return integer;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Calibration manifest integer is invalid: " + name, exception);
        }
    }

    public enum Partition {
        DEVELOPMENT,
        HELD_OUT;

        private static Partition from(String value) {
            if ("development".equals(value)) {
                return DEVELOPMENT;
            }
            if ("held_out".equals(value)) {
                return HELD_OUT;
            }
            throw new IllegalArgumentException("Unknown calibration partition: " + value);
        }
    }

    public enum Layout {
        NEAR,
        FAR,
        BACKGROUND;

        private static Layout from(String value) {
            if ("near".equals(value)) {
                return NEAR;
            }
            if ("far".equals(value)) {
                return FAR;
            }
            if ("background".equals(value)) {
                return BACKGROUND;
            }
            throw new IllegalArgumentException("Unknown calibration layout: " + value);
        }
    }

    public enum CaptureRole {
        TARGET,
        TEMPORAL,
        BACKGROUND;

        private static CaptureRole from(String value) {
            if ("target".equals(value)) {
                return TARGET;
            }
            if ("temporal".equals(value)) {
                return TEMPORAL;
            }
            if ("background".equals(value)) {
                return BACKGROUND;
            }
            throw new IllegalArgumentException("Unknown calibration capture role: " + value);
        }
    }

    public static final class Pair {
        private final String code;
        private final Partition partition;
        private final int classA;
        private final String identityA;
        private final int classB;
        private final String identityB;

        private Pair(
            String code,
            Partition partition,
            int classA,
            String identityA,
            int classB,
            String identityB
        ) {
            if (code == null || code.isEmpty() || classA <= 0 || classB <= 0 || classA == classB
                || identityA == null || identityA.isEmpty()
                || identityB == null || identityB.isEmpty()) {
                throw new IllegalArgumentException("Calibration pair is invalid");
            }
            this.code = code;
            this.partition = partition;
            this.classA = classA;
            this.identityA = identityA;
            this.classB = classB;
            this.identityB = identityB;
        }

        public String getCode() {
            return code;
        }

        public Partition getPartition() {
            return partition;
        }

        public int getClassA() {
            return classA;
        }

        public String getIdentityA() {
            return identityA;
        }

        public int getClassB() {
            return classB;
        }

        public String getIdentityB() {
            return identityB;
        }
    }

    public static final class Capture {
        private final String captureName;
        private final String condition;
        private final int conditionOrdinal;
        private final Layout layout;
        private final int cameraOrdinal;
        private final int repeatIndex;
        private final CaptureRole role;
        private final String temporalGroup;

        private Capture(
            String captureName,
            String condition,
            int conditionOrdinal,
            Layout layout,
            int cameraOrdinal,
            int repeatIndex,
            CaptureRole role,
            String temporalGroup
        ) {
            if (captureName == null || captureName.isEmpty()
                || condition == null || condition.isEmpty()
                || conditionOrdinal < 0 || conditionOrdinal > 13
                || cameraOrdinal < 0 || repeatIndex < 0
                || layout == null || role == null) {
                throw new IllegalArgumentException("Calibration capture entry is invalid");
            }
            if (role == CaptureRole.TEMPORAL && temporalGroup == null) {
                throw new IllegalArgumentException("Temporal calibration capture needs a group");
            }
            if (role == CaptureRole.BACKGROUND && layout != Layout.BACKGROUND) {
                throw new IllegalArgumentException("Background capture needs the background layout");
            }
            String expectedCondition = String.format(
                java.util.Locale.ROOT,
                "C%02d",
                conditionOrdinal + 1
            );
            if (!expectedCondition.equals(condition)) {
                throw new IllegalArgumentException("Calibration condition ordinal does not match");
            }
            int expectedCameraOrdinal = layout == Layout.FAR ? 1 : 0;
            if (cameraOrdinal != expectedCameraOrdinal) {
                throw new IllegalArgumentException("Calibration camera ordinal does not match layout");
            }
            if (role == CaptureRole.TEMPORAL && repeatIndex == 0
                || role != CaptureRole.TEMPORAL && repeatIndex != 0) {
                throw new IllegalArgumentException("Calibration repeat index does not match role");
            }
            this.captureName = captureName;
            this.condition = condition;
            this.conditionOrdinal = conditionOrdinal;
            this.layout = layout;
            this.cameraOrdinal = cameraOrdinal;
            this.repeatIndex = repeatIndex;
            this.role = role;
            this.temporalGroup = temporalGroup;
        }

        public String getCaptureName() {
            return captureName;
        }

        public String getCondition() {
            return condition;
        }

        public int getConditionOrdinal() {
            return conditionOrdinal;
        }

        public Layout getLayout() {
            return layout;
        }

        public int getCameraOrdinal() {
            return cameraOrdinal;
        }

        public int getRepeatIndex() {
            return repeatIndex;
        }

        public CaptureRole getRole() {
            return role;
        }

        public String getTemporalGroup() {
            return temporalGroup;
        }
    }
}
