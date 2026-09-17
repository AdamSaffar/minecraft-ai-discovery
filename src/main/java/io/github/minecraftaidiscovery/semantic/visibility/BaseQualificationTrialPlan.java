package io.github.minecraftaidiscovery.semantic.visibility;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BaseQualificationTrialPlan {
    static final int EXPECTED_TARGETS = 150;
    static final int EXPECTED_PRESENTATIONS = 450;

    private final Map<String, BaseQualificationCapture> captures;
    private final BaseQualificationTargetGeometry nearGeometry;
    private final BaseQualificationTargetGeometry farGeometry;
    private final Map<String, BaseQualificationTargetGeometry> geometryByCapture;
    private final List<BaseQualificationTarget> targets;
    private final List<BaseQualificationPresentation> presentations;

    private BaseQualificationTrialPlan(
        Map<String, BaseQualificationCapture> captures,
        BaseQualificationTargetGeometry nearGeometry,
        BaseQualificationTargetGeometry farGeometry,
        Map<String, BaseQualificationTargetGeometry> geometryByCapture,
        List<BaseQualificationTarget> targets,
        List<BaseQualificationPresentation> presentations
    ) {
        this.captures = Collections.unmodifiableMap(new LinkedHashMap<>(captures));
        this.nearGeometry = nearGeometry;
        this.farGeometry = farGeometry;
        this.geometryByCapture = Collections.unmodifiableMap(
            new LinkedHashMap<>(geometryByCapture)
        );
        this.targets = Collections.unmodifiableList(targets);
        this.presentations = Collections.unmodifiableList(presentations);
    }

    static BaseQualificationTrialPlan create(
        BaseQualificationStudyManifest manifest,
        Map<String, BaseQualificationCapture> captures
    ) {
        if (manifest == null || captures == null
            || captures.size() != BaseQualificationStudyManifest.EXPECTED_CAPTURE_COUNT) {
            throw new IllegalArgumentException("Accepted calibration captures are incomplete");
        }
        for (BaseQualificationStudyManifest.Capture entry : manifest.getCaptures()) {
            if (!captures.containsKey(entry.getCaptureName())) {
                throw new IllegalArgumentException(
                    "Accepted calibration capture was not loaded: " + entry.getCaptureName()
                );
            }
        }
        BaseQualificationTargetGeometry near = BaseQualificationTargetGeometry.derive(
            requireCapture(captures, manifest.getReferenceNearCapture()),
            manifest.getPairs()
        );
        BaseQualificationTargetGeometry far = BaseQualificationTargetGeometry.derive(
            requireCapture(captures, manifest.getReferenceFarCapture()),
            manifest.getPairs()
        );
        Map<String, BaseQualificationTargetGeometry> geometryByCapture =
            validateCaptureSet(manifest, captures, near, far);

        Map<String, BaseQualificationTarget> uniqueTargets = new LinkedHashMap<>();
        // The modulo schedule and canonical key implement the predeclared bounded target set.
        for (BaseQualificationStudyManifest.Capture capture : manifest.getCaptures()) {
            if (capture.getRole() != BaseQualificationStudyManifest.CaptureRole.TARGET) {
                continue;
            }
            for (int pairIndex = 0; pairIndex < manifest.getPairs().size(); pairIndex++) {
                BaseQualificationStudyManifest.Pair pair = manifest.getPairs().get(pairIndex);
                BaseQualificationTargetRegion region = BaseQualificationTargetRegion
                    .fromScheduleIndex(
                        capture.getConditionOrdinal()
                            + capture.getCameraOrdinal()
                            + pairIndex
                    );
                addTarget(manifest, uniqueTargets, capture, pair, region, geometryByCapture);
            }
        }
        BaseQualificationStudyManifest.Capture positive = requireManifestCapture(
            manifest,
            manifest.getReferenceNearCapture()
        );
        BaseQualificationStudyManifest.Capture negative = requireManifestCapture(
            manifest,
            manifest.getInsideBlockCapture()
        );
        // Explicit catch/control additions intentionally deduplicate scheduled equivalents.
        for (BaseQualificationStudyManifest.Pair pair : manifest.getPairs()) {
            addTarget(
                manifest,
                uniqueTargets,
                positive,
                pair,
                BaseQualificationTargetRegion.LARGE_A,
                geometryByCapture
            );
            addTarget(
                manifest,
                uniqueTargets,
                positive,
                pair,
                BaseQualificationTargetRegion.LARGE_B,
                geometryByCapture
            );
            addTarget(
                manifest,
                uniqueTargets,
                negative,
                pair,
                BaseQualificationTargetRegion.LARGE_A,
                geometryByCapture
            );
            addTarget(
                manifest,
                uniqueTargets,
                negative,
                pair,
                BaseQualificationTargetRegion.LARGE_B,
                geometryByCapture
            );
        }
        if (uniqueTargets.size() != EXPECTED_TARGETS) {
            throw new IllegalArgumentException(
                "Frozen calibration schedule produced " + uniqueTargets.size()
                    + " targets instead of " + EXPECTED_TARGETS
            );
        }
        List<BaseQualificationTarget> targets = new ArrayList<>(uniqueTargets.values());
        List<BaseQualificationPresentation> presentations = createPresentations(
            manifest.getRandomSeed(),
            targets
        );
        return new BaseQualificationTrialPlan(
            captures,
            near,
            far,
            geometryByCapture,
            targets,
            presentations
        );
    }

    Map<String, BaseQualificationCapture> getCaptures() {
        return captures;
    }

    BaseQualificationTargetGeometry geometryFor(BaseQualificationStudyManifest.Layout layout) {
        if (layout == BaseQualificationStudyManifest.Layout.NEAR) {
            return nearGeometry;
        }
        if (layout == BaseQualificationStudyManifest.Layout.FAR) {
            return farGeometry;
        }
        throw new IllegalArgumentException("Background layout has no target geometry");
    }

    List<BaseQualificationTarget> getTargets() {
        return targets;
    }

    List<BaseQualificationPresentation> getPresentations() {
        return presentations;
    }

    BaseQualificationTargetGeometry geometryForCapture(String captureName) {
        BaseQualificationTargetGeometry geometry = geometryByCapture.get(captureName);
        if (geometry == null) {
            throw new IllegalArgumentException(
                "Calibration capture has no target geometry: " + captureName
            );
        }
        return geometry;
    }

    private static Map<String, BaseQualificationTargetGeometry> validateCaptureSet(
        BaseQualificationStudyManifest manifest,
        Map<String, BaseQualificationCapture> captures,
        BaseQualificationTargetGeometry near,
        BaseQualificationTargetGeometry far
    ) {
        Map<String, BaseQualificationTargetGeometry> geometryByCapture = new LinkedHashMap<>();
        for (BaseQualificationStudyManifest.Capture entry : manifest.getCaptures()) {
            BaseQualificationCapture capture = requireCapture(captures, entry.getCaptureName());
            byte[] reasons = capture.getComposition().copyReasons();
            for (byte reason : reasons) {
                int code = Byte.toUnsignedInt(reason);
                if (code != BaseInfluenceReasonEncoding.BACKGROUND
                    && code != BaseInfluenceReasonEncoding.SUPPORTED) {
                    throw new IllegalArgumentException(
                        "Primary calibration matrix contains unsupported pixels: "
                            + capture.getCaptureName()
                    );
                }
            }
            if (entry.getLayout() == BaseQualificationStudyManifest.Layout.NEAR) {
                BaseQualificationTargetGeometry geometry = entry.getCaptureName().equals(
                    manifest.getReferenceNearCapture()
                ) ? near : BaseQualificationTargetGeometry.derive(capture, manifest.getPairs());
                geometryByCapture.put(entry.getCaptureName(), geometry);
            } else if (entry.getLayout() == BaseQualificationStudyManifest.Layout.FAR) {
                BaseQualificationTargetGeometry geometry = entry.getCaptureName().equals(
                    manifest.getReferenceFarCapture()
                ) ? far : BaseQualificationTargetGeometry.derive(capture, manifest.getPairs());
                geometryByCapture.put(entry.getCaptureName(), geometry);
            } else {
                int[] classes = capture.copyBaseClassIds();
                for (int classId : classes) {
                    if (classId != 0) {
                        throw new IllegalArgumentException("Sky control contains positive base pixels");
                    }
                }
            }
        }
        validateTemporalGeometry(manifest, captures);
        return geometryByCapture;
    }

    private static void validateTemporalGeometry(
        BaseQualificationStudyManifest manifest,
        Map<String, BaseQualificationCapture> captures
    ) {
        for (BaseQualificationStudyManifest.Capture representative : manifest.getCaptures()) {
            if (representative.getRole() != BaseQualificationStudyManifest.CaptureRole.TARGET
                || representative.getTemporalGroup() == null) {
                continue;
            }
            BaseQualificationCapture reference = requireCapture(
                captures,
                representative.getCaptureName()
            );
            int[] referenceClasses = reference.copyBaseClassIds();
            int[] referenceDepth = reference.copyBaseDepthBits();
            for (BaseQualificationStudyManifest.Capture frame : manifest.getCaptures()) {
                if (!representative.getTemporalGroup().equals(frame.getTemporalGroup())) {
                    continue;
                }
                BaseQualificationCapture candidate = requireCapture(
                    captures,
                    frame.getCaptureName()
                );
                if (!Arrays.equals(referenceClasses, candidate.copyBaseClassIds())
                    || !Arrays.equals(referenceDepth, candidate.copyBaseDepthBits())) {
                    throw new IllegalArgumentException(
                        "Calibration temporal geometry changed in " + frame.getCaptureName()
                    );
                }
            }
        }
    }

    private static void addTarget(
        BaseQualificationStudyManifest manifest,
        Map<String, BaseQualificationTarget> targets,
        BaseQualificationStudyManifest.Capture capture,
        BaseQualificationStudyManifest.Pair pair,
        BaseQualificationTargetRegion region,
        Map<String, BaseQualificationTargetGeometry> geometryByCapture
    ) {
        String canonical = capture.getCaptureName() + "|" + pair.getCode() + "|" + region.name();
        if (targets.containsKey(canonical)) {
            return;
        }
        BaseQualificationTarget.Control control = BaseQualificationTarget.Control.NONE;
        if (capture.getCaptureName().equals(manifest.getReferenceNearCapture())
            && region.isLarge()) {
            control = BaseQualificationTarget.Control.POSITIVE_CATCH;
        } else if (capture.getCaptureName().equals(manifest.getInsideBlockCapture())
            && region.isLarge()) {
            control = BaseQualificationTarget.Control.NEGATIVE_BLINDING;
        }
        BaseQualificationTargetGeometry geometry = geometryByCapture.get(capture.getCaptureName());
        if (geometry == null) {
            throw new IllegalArgumentException(
                "Calibration target geometry is missing: " + capture.getCaptureName()
            );
        }
        String targetId = opaqueId(manifest.getRandomSeed(), "target|" + canonical);
        targets.put(
            canonical,
            new BaseQualificationTarget(
                targetId,
                capture,
                pair,
                region,
                control,
                geometry.requireMask(pair.getCode(), region)
            )
        );
    }

    private static List<BaseQualificationPresentation> createPresentations(
        String seed,
        List<BaseQualificationTarget> targets
    ) {
        List<BaseQualificationPresentation> presentations = new ArrayList<>();
        for (int batch = 1; batch <= 3; batch++) {
            final int batchNumber = batch;
            List<BaseQualificationPresentation> batchTrials = new ArrayList<>();
            for (BaseQualificationTarget target : targets) {
                String material = "trial|" + batchNumber + "|" + target.getTargetId();
                byte[] decision = digest(seed + "|" + material);
                batchTrials.add(
                    new BaseQualificationPresentation(
                        opaqueId(seed, material),
                        batchNumber,
                        target,
                        (decision[0] & 1) == 0,
                        (decision[0] & 2) != 0
                    )
                );
            }
            batchTrials.sort(
                Comparator.comparing(
                    trial -> opaqueId(
                        seed,
                        "order|" + batchNumber + "|" + trial.getTrialId()
                    )
                )
            );
            presentations.addAll(batchTrials);
        }
        if (presentations.size() != EXPECTED_PRESENTATIONS) {
            throw new IllegalStateException("Calibration presentation count is invalid");
        }
        return presentations;
    }

    private static BaseQualificationCapture requireCapture(
        Map<String, BaseQualificationCapture> captures,
        String name
    ) {
        BaseQualificationCapture capture = captures.get(name);
        if (capture == null) {
            throw new IllegalArgumentException("Calibration capture is missing: " + name);
        }
        return capture;
    }

    private static BaseQualificationStudyManifest.Capture requireManifestCapture(
        BaseQualificationStudyManifest manifest,
        String name
    ) {
        for (BaseQualificationStudyManifest.Capture capture : manifest.getCaptures()) {
            if (capture.getCaptureName().equals(name)) {
                return capture;
            }
        }
        throw new IllegalArgumentException("Calibration manifest capture is missing: " + name);
    }

    static String opaqueId(String seed, String material) {
        byte[] hash = digest(seed + "|" + material);
        StringBuilder id = new StringBuilder(24);
        for (int index = 0; index < 12; index++) {
            id.append(String.format(Locale.ROOT, "%02x", Byte.toUnsignedInt(hash[index])));
        }
        return id.toString();
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
