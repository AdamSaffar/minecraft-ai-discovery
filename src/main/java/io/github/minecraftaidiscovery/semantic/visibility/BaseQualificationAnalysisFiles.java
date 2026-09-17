package io.github.minecraftaidiscovery.semantic.visibility;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class BaseQualificationAnalysisFiles {
    private static final String INPUT_LOCK = "input-lock.json";
    private static final String DEVELOPMENT_KEY = "sealed/development-key.json";
    private static final String HELD_OUT_KEY = "sealed/held-out-key.json";
    private static final String DEVELOPMENT_ANALYSIS = "development-analysis.json";
    private static final String DEVELOPMENT_FREEZE = "development-freeze.json";
    private static final String HELD_OUT_ANALYSIS = "held-out-analysis.json";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    private BaseQualificationAnalysisFiles() {
    }

    static void freezeDevelopment(
        Path manifestPath,
        Path studyDirectory,
        Path responsePath,
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan
    ) throws IOException {
        Path study = requireDirectory(studyDirectory);
        Path analysisPath = study.resolve(DEVELOPMENT_ANALYSIS);
        Path freezePath = study.resolve(DEVELOPMENT_FREEZE);
        requireAbsent(analysisPath);
        requireAbsent(freezePath);
        InputLock lock = verifyInputLock(manifestPath, study, manifest, plan);
        requireHash(study.resolve(DEVELOPMENT_KEY), lock.developmentKeySha256, DEVELOPMENT_KEY);
        BaseQualificationResponseSet responses = BaseQualificationResponseSet.read(
            responsePath,
            BaseQualificationStudyManifest.Partition.DEVELOPMENT,
            plan
        );
        BaseQualificationCandidateEvaluator.FamilyResult family =
            BaseQualificationCandidateEvaluator.evaluateDevelopmentFamily(
                manifest,
                plan,
                responses
            );
        JsonObject analysis = analysisRoot(
            "development",
            manifest,
            plan,
            responses,
            family
        );
        writeNewJson(analysisPath, analysis);

        String status;
        if (!responses.isSessionValid()) {
            status = "invalid_session";
        } else if (family.getSelected() == null) {
            status = "simple_family_failed";
        } else {
            status = "frozen_candidate";
        }
        JsonObject freeze = new JsonObject();
        freeze.addProperty("format", "base-identity-qualification-development-freeze-v1");
        freeze.addProperty("researcherOnly", true);
        freeze.addProperty("agentVisible", false);
        freeze.addProperty("status", status);
        freeze.addProperty("inputLockSha256", sha256(study.resolve(INPUT_LOCK)));
        freeze.addProperty("studyManifestSha256", sha256(manifestPath));
        freeze.addProperty("developmentResponsesSha256", sha256(responsePath));
        freeze.addProperty("developmentAnalysisSha256", sha256(analysisPath));
        freeze.addProperty("sealedHeldOutKeySha256", lock.heldOutKeySha256);
        freeze.add("candidateFamily", candidateFamily());
        if (family.getSelected() != null && responses.isSessionValid()) {
            freeze.add("candidate", candidate(family.getSelected().getCandidate()));
        }
        writeNewJson(freezePath, freeze);
        if ("frozen_candidate".equals(status)) {
            BaseQualificationTrialWriter.releaseHeldOutLabeler(study);
        }
    }

    static void evaluateHeldOut(
        Path manifestPath,
        Path studyDirectory,
        Path responsePath,
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan
    ) throws IOException {
        Path study = requireDirectory(studyDirectory);
        Path output = study.resolve(HELD_OUT_ANALYSIS);
        requireAbsent(output);
        InputLock lock = verifyInputLock(manifestPath, study, manifest, plan);
        Path freezePath = study.resolve(DEVELOPMENT_FREEZE);
        JsonObject freeze = readObject(freezePath, DEVELOPMENT_FREEZE);
        requireString(
            freeze,
            "format",
            "base-identity-qualification-development-freeze-v1"
        );
        requireString(freeze, "status", "frozen_candidate");
        requireString(freeze, "inputLockSha256", sha256(study.resolve(INPUT_LOCK)));
        requireString(freeze, "studyManifestSha256", sha256(manifestPath));
        requireString(freeze, "sealedHeldOutKeySha256", lock.heldOutKeySha256);
        requireString(
            freeze,
            "developmentAnalysisSha256",
            sha256(study.resolve(DEVELOPMENT_ANALYSIS))
        );
        requireCandidateFamily(requireObject(freeze, "candidateFamily"));
        if (!Files.isDirectory(study.resolve("labeler-held-out"), LinkOption.NOFOLLOW_LINKS)
            || Files.exists(study.resolve("sealed/labeler-held-out"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Held-out labeler has not been released");
        }
        BaseQualificationCandidate selected = readCandidate(requireObject(freeze, "candidate"));
        // The held-out key is not touched until every development-freeze check above succeeds.
        requireHash(study.resolve(HELD_OUT_KEY), lock.heldOutKeySha256, HELD_OUT_KEY);
        BaseQualificationResponseSet responses = BaseQualificationResponseSet.read(
            responsePath,
            BaseQualificationStudyManifest.Partition.HELD_OUT,
            plan
        );
        BaseQualificationCandidateEvaluator.CandidateResult result =
            BaseQualificationCandidateEvaluator.evaluateFrozenCandidate(
                manifest,
                plan,
                responses,
                selected
            );
        JsonObject analysis = analysisRoot(
            "held_out",
            manifest,
            plan,
            responses,
            result
        );
        analysis.addProperty("developmentFreezeSha256", sha256(freezePath));
        analysis.addProperty("heldOutResponsesSha256", sha256(responsePath));
        writeNewJson(output, analysis);
    }

    private static JsonObject analysisRoot(
        String partition,
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan,
        BaseQualificationResponseSet responses,
        BaseQualificationCandidateEvaluator.FamilyResult family
    ) {
        JsonObject root = commonAnalysis(partition, manifest, plan, responses);
        JsonArray candidates = new JsonArray();
        for (BaseQualificationCandidateEvaluator.CandidateResult result
            : family.getCandidates()) {
            candidates.add(candidateResult(result));
        }
        root.add("candidates", candidates);
        if (family.getSelected() != null) {
            root.add("selectedCandidate", candidateResult(family.getSelected()));
        }
        root.add("contrastDiagnostics", contrastDiagnostics(
            manifest,
            plan,
            responses.getPartition()
        ));
        return root;
    }

    private static JsonObject analysisRoot(
        String partition,
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan,
        BaseQualificationResponseSet responses,
        BaseQualificationCandidateEvaluator.CandidateResult result
    ) {
        JsonObject root = commonAnalysis(partition, manifest, plan, responses);
        root.add("frozenCandidateResult", candidateResult(result));
        root.add("contrastDiagnostics", contrastDiagnostics(
            manifest,
            plan,
            responses.getPartition()
        ));
        return root;
    }

    private static JsonObject commonAnalysis(
        String partition,
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan,
        BaseQualificationResponseSet responses
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("format", "base-identity-qualification-analysis-v1");
        root.addProperty("partition", partition);
        root.addProperty("researcherOnly", true);
        root.addProperty("agentVisible", false);
        root.addProperty("thresholdIsProductionPolicy", false);
        root.addProperty("responseCount", responses.getResponseCount());
        root.addProperty("positiveCatchCorrect", responses.getPositiveCatchCorrect());
        root.addProperty("positiveCatchTotal", responses.getPositiveCatchTotal());
        root.addProperty("negativeControlCorrect", responses.getNegativeControlCorrect());
        root.addProperty("negativeControlTotal", responses.getNegativeControlTotal());
        root.addProperty(
            "negativeControlTwoSidedPValue",
            responses.getNegativeControlTwoSidedPValue()
        );
        root.addProperty("sessionValid", responses.isSessionValid());
        root.addProperty("blockClassManifestSha256", manifest.getManifestSha256());
        root.addProperty("targetCount", countTargets(plan, responses.getPartition()));
        root.add("candidateFamily", candidateFamily());
        root.add("targetLabels", targetLabels(responses, plan));
        return root;
    }

    private static JsonObject candidateResult(
        BaseQualificationCandidateEvaluator.CandidateResult result
    ) {
        JsonObject json = candidate(result.getCandidate());
        json.addProperty("recognizableTargets", result.getRecognizableTargets());
        json.addProperty("retainedRecognizableTargets", result.getRetainedRecognizableTargets());
        json.addProperty("recall", result.getRecall());
        json.addProperty("unsafeReveals", result.getUnsafeReveals());
        json.addProperty("forbiddenQualifiedPixels", result.getForbiddenQualifiedPixels());
        json.addProperty("temporalToggles", result.getTemporalToggles());
        json.addProperty("maximumBlockLightChange", result.getMaximumBlockLightChange());
        json.addProperty("passed", result.isPassed());
        JsonObject qualifications = new JsonObject();
        for (Map.Entry<String, Boolean> entry : result.getQualifiedByTarget().entrySet()) {
            qualifications.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("targetQualifications", qualifications);
        return json;
    }

    private static JsonObject candidate(BaseQualificationCandidate candidate) {
        JsonObject json = new JsonObject();
        json.addProperty("id", candidate.getId());
        json.addProperty("reducer", candidate.getReducer().getMetadataName());
        json.addProperty("threshold", candidate.getThreshold());
        json.addProperty("minimumArea", candidate.getMinimumArea());
        json.addProperty("usesClassConnectedComponents", true);
        json.addProperty("boundaryErosionPixels", 1);
        json.addProperty("exactZeroOverride", true);
        return json;
    }

    private static JsonObject candidateFamily() {
        JsonObject family = new JsonObject();
        JsonArray reducers = new JsonArray();
        for (BaseQualificationCandidate.Reducer reducer
            : BaseQualificationCandidate.Reducer.values()) {
            reducers.add(reducer.getMetadataName());
        }
        family.add("reducers", reducers);
        family.add("thresholds", intArray(BaseQualificationCandidateEvaluator.THRESHOLDS));
        family.add("minimumAreas", intArray(BaseQualificationCandidateEvaluator.MINIMUM_AREAS));
        family.addProperty("componentConnectivity", 4);
        family.addProperty("sameClassComponents", true);
        family.addProperty("boundaryErosionPixels", 1);
        family.addProperty("exactZeroOverride", true);
        family.addProperty("developmentMinimumRecall", 0.60);
        family.addProperty("heldOutMinimumRecall", 0.50);
        family.addProperty("maximumBlockLightTemporalChange", 0.005);
        return family;
    }

    private static JsonArray contrastDiagnostics(
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan,
        BaseQualificationStudyManifest.Partition partition
    ) {
        JsonArray diagnostics = new JsonArray();
        Map<String, JsonObject> unique = new LinkedHashMap<>();
        for (BaseQualificationTarget target : plan.getTargets()) {
            if (target.getPair().getPartition() != partition) {
                continue;
            }
            String key = target.getCapture().getCaptureName() + "|" + target.getPair().getCode();
            if (unique.containsKey(key)) {
                continue;
            }
            BaseQualificationCapture capture = plan.getCaptures().get(
                target.getCapture().getCaptureName()
            );
            BaseQualificationContrastDiagnostic.Result result =
                BaseQualificationContrastDiagnostic.calculate(
                    capture,
                    plan.geometryForCapture(target.getCapture().getCaptureName()),
                    target.getPair().getCode()
                );
            JsonObject json = new JsonObject();
            json.addProperty("capture", target.getCapture().getCaptureName());
            json.addProperty("condition", target.getCapture().getCondition());
            json.addProperty("pair", target.getPair().getCode());
            json.addProperty("boundaryMedianRgbDistance", result.getBoundaryMedian());
            json.addProperty("withinClassMedianRgbDistance", result.getWithinClassMedian());
            if (Double.isFinite(result.getRatio())) {
                json.addProperty("boundaryToWithinRatio", result.getRatio());
            } else {
                json.addProperty("boundaryToWithinRatio", "infinite");
            }
            unique.put(key, json);
        }
        for (JsonObject value : unique.values()) {
            diagnostics.add(value);
        }
        return diagnostics;
    }

    private static InputLock verifyInputLock(
        Path manifestPath,
        Path study,
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan
    ) throws IOException {
        JsonObject lock = readObject(study.resolve(INPUT_LOCK), INPUT_LOCK);
        requireString(lock, "format", "base-identity-qualification-input-lock-v1");
        requireBoolean(lock, "researcherOnly", true);
        requireBoolean(lock, "agentVisible", false);
        requireBoolean(lock, "candidateFamilyFrozen", true);
        requireString(lock, "studyManifestSha256", sha256(manifestPath));
        requireString(lock, "blockClassManifestSha256", manifest.getManifestSha256());
        requireInt(lock, "acceptedCaptureCount", plan.getCaptures().size());
        requireInt(lock, "uniqueTargetCount", plan.getTargets().size());
        requireInt(lock, "presentationCount", plan.getPresentations().size());
        String developmentHash = requireString(lock, "developmentKeySha256");
        String heldOutHash = requireString(lock, "heldOutKeySha256");
        verifyCaptureHashes(requireArray(lock, "captures"), plan);
        return new InputLock(developmentHash, heldOutHash);
    }

    private static JsonArray targetLabels(
        BaseQualificationResponseSet responses,
        BaseQualificationTrialPlan plan
    ) {
        JsonArray labels = new JsonArray();
        for (BaseQualificationTarget target : plan.getTargets()) {
            if (target.getPair().getPartition() != responses.getPartition()) {
                continue;
            }
            BaseQualificationResponseSet.TargetLabel label = responses.requireLabel(
                target.getTargetId()
            );
            JsonObject json = new JsonObject();
            json.addProperty("targetId", target.getTargetId());
            json.addProperty("recognizable", label.isRecognizable());
            json.addProperty("stable", label.isStable());
            JsonArray outcomes = new JsonArray();
            for (BaseQualificationResponseSet.Outcome outcome : label.getOutcomes()) {
                outcomes.add(outcome.name().toLowerCase(Locale.ROOT));
            }
            json.add("outcomes", outcomes);
            labels.add(json);
        }
        return labels;
    }

    private static void verifyCaptureHashes(
        JsonArray lockedCaptures,
        BaseQualificationTrialPlan plan
    ) {
        if (lockedCaptures.size() != plan.getCaptures().size()) {
            throw new IllegalArgumentException("Calibration input lock capture count changed");
        }
        Map<String, BaseQualificationCapture> remaining = new LinkedHashMap<>(plan.getCaptures());
        for (JsonElement element : lockedCaptures) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Calibration input lock capture is invalid");
            }
            JsonObject captureJson = element.getAsJsonObject();
            String name = requireString(captureJson, "capture");
            BaseQualificationCapture capture = remaining.remove(name);
            if (capture == null) {
                throw new IllegalArgumentException("Calibration input lock has an unknown capture");
            }
            JsonObject hashes = requireObject(captureJson, "sha256");
            if (hashes.size() != capture.getArtifactSha256().size()) {
                throw new IllegalArgumentException("Calibration input lock artifact count changed");
            }
            for (Map.Entry<String, String> entry : capture.getArtifactSha256().entrySet()) {
                requireString(hashes, entry.getKey(), entry.getValue());
            }
        }
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException("Calibration input lock omits accepted captures");
        }
    }

    private static BaseQualificationCandidate readCandidate(JsonObject json) {
        return new BaseQualificationCandidate(
            BaseQualificationCandidate.Reducer.fromMetadataName(requireString(json, "reducer")),
            requireInt(json, "threshold"),
            requireInt(json, "minimumArea")
        );
    }

    private static void requireCandidateFamily(JsonObject actual) {
        if (!candidateFamily().equals(actual)) {
            throw new IllegalArgumentException("Frozen calibration candidate family changed");
        }
    }

    private static int countTargets(
        BaseQualificationTrialPlan plan,
        BaseQualificationStudyManifest.Partition partition
    ) {
        int count = 0;
        for (BaseQualificationTarget target : plan.getTargets()) {
            if (target.getPair().getPartition() == partition) {
                count++;
            }
        }
        return count;
    }

    private static JsonArray intArray(int[] values) {
        JsonArray array = new JsonArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }

    private static Path requireDirectory(Path path) {
        if (path == null || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Calibration study directory is missing");
        }
        return path.toAbsolutePath().normalize();
    }

    private static void requireAbsent(Path path) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Calibration result already exists: " + path);
        }
    }

    private static void requireHash(Path path, String expected, String name) throws IOException {
        String actual = sha256(path);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Calibration file hash changed: " + name);
        }
    }

    private static JsonObject readObject(Path path, String name) throws IOException {
        requireRegularFile(path, name);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Calibration JSON must be an object: " + name);
            }
            return element.getAsJsonObject();
        }
    }

    private static void writeNewJson(Path destination, JsonObject value) throws IOException {
        requireAbsent(destination);
        Path parent = destination.toAbsolutePath().normalize().getParent();
        Path temporary = Files.createTempFile(parent, destination.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, (JSON.toJson(value) + "\n").getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static String sha256(Path path) throws IOException {
        requireRegularFile(path, path.getFileName().toString());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (java.io.InputStream input = Files.newInputStream(path)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
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

    private static void requireRegularFile(Path path, String name) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
            || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("Calibration file is missing or linked: " + name);
        }
    }

    private static JsonObject requireObject(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("Calibration JSON object is missing: " + name);
        }
        return value.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("Calibration JSON array is missing: " + name);
        }
        return value.getAsJsonArray();
    }

    private static String requireString(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
            || value.getAsString().isEmpty()) {
            throw new IllegalArgumentException("Calibration JSON string is missing: " + name);
        }
        return value.getAsString();
    }

    private static void requireString(JsonObject parent, String name, String expected) {
        String actual = requireString(parent, name);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Calibration JSON " + name + " changed");
        }
    }

    private static int requireInt(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Calibration JSON integer is missing: " + name);
        }
        try {
            int integer = value.getAsInt();
            if (!Integer.toString(integer).equals(value.getAsString())) {
                throw new IllegalArgumentException("Calibration JSON integer is not exact: " + name);
            }
            return integer;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Calibration JSON integer is invalid: " + name, exception);
        }
    }

    private static void requireInt(JsonObject parent, String name, int expected) {
        if (requireInt(parent, name) != expected) {
            throw new IllegalArgumentException("Calibration JSON " + name + " changed");
        }
    }

    private static void requireBoolean(JsonObject parent, String name, boolean expected) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()
            || value.getAsBoolean() != expected) {
            throw new IllegalArgumentException("Calibration JSON boolean is invalid: " + name);
        }
    }

    private static final class InputLock {
        private final String developmentKeySha256;
        private final String heldOutKeySha256;

        private InputLock(String developmentKeySha256, String heldOutKeySha256) {
            this.developmentKeySha256 = developmentKeySha256;
            this.heldOutKeySha256 = heldOutKeySha256;
        }
    }
}
