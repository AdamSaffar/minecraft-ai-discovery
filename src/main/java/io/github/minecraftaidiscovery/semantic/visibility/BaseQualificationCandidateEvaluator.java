package io.github.minecraftaidiscovery.semantic.visibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BaseQualificationCandidateEvaluator {
    static final int[] THRESHOLDS = {
        1, 4, 8, 16, 24, 32, 48, 64, 96, 128, 160, 192, 224
    };
    static final int[] MINIMUM_AREAS = {1, 4, 16, 64, 256};
    static final double DEVELOPMENT_MINIMUM_RECALL = 0.60;
    static final double HELD_OUT_MINIMUM_RECALL = 0.50;
    static final double MAXIMUM_BLOCK_LIGHT_CHANGE = 0.005;

    static final class CandidateResult {
        private final BaseQualificationCandidate candidate;
        private final Map<String, Boolean> qualifiedByTarget;
        private final int recognizableTargets;
        private final int retainedRecognizableTargets;
        private final int unsafeReveals;
        private final int forbiddenQualifiedPixels;
        private final int temporalToggles;
        private final double maximumBlockLightChange;
        private final boolean passed;

        private CandidateResult(
            BaseQualificationCandidate candidate,
            Map<String, Boolean> qualifiedByTarget,
            int recognizableTargets,
            int retainedRecognizableTargets,
            int unsafeReveals,
            int forbiddenQualifiedPixels,
            int temporalToggles,
            double maximumBlockLightChange,
            boolean passed
        ) {
            this.candidate = candidate;
            this.qualifiedByTarget = Collections.unmodifiableMap(
                new LinkedHashMap<>(qualifiedByTarget)
            );
            this.recognizableTargets = recognizableTargets;
            this.retainedRecognizableTargets = retainedRecognizableTargets;
            this.unsafeReveals = unsafeReveals;
            this.forbiddenQualifiedPixels = forbiddenQualifiedPixels;
            this.temporalToggles = temporalToggles;
            this.maximumBlockLightChange = maximumBlockLightChange;
            this.passed = passed;
        }

        BaseQualificationCandidate getCandidate() {
            return candidate;
        }

        Map<String, Boolean> getQualifiedByTarget() {
            return qualifiedByTarget;
        }

        int getRecognizableTargets() {
            return recognizableTargets;
        }

        int getRetainedRecognizableTargets() {
            return retainedRecognizableTargets;
        }

        int getUnsafeReveals() {
            return unsafeReveals;
        }

        int getForbiddenQualifiedPixels() {
            return forbiddenQualifiedPixels;
        }

        int getTemporalToggles() {
            return temporalToggles;
        }

        double getMaximumBlockLightChange() {
            return maximumBlockLightChange;
        }

        double getRecall() {
            return recognizableTargets == 0
                ? 0.0
                : retainedRecognizableTargets / (double)recognizableTargets;
        }

        boolean isPassed() {
            return passed;
        }
    }

    static final class FamilyResult {
        private final List<CandidateResult> candidates;
        private final CandidateResult selected;

        private FamilyResult(List<CandidateResult> candidates, CandidateResult selected) {
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
            this.selected = selected;
        }

        List<CandidateResult> getCandidates() {
            return candidates;
        }

        CandidateResult getSelected() {
            return selected;
        }
    }

    private BaseQualificationCandidateEvaluator() {
    }

    static FamilyResult evaluateDevelopmentFamily(
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan,
        BaseQualificationResponseSet responses
    ) {
        requirePartition(responses, BaseQualificationStudyManifest.Partition.DEVELOPMENT);
        Map<String, PreparedCapture> prepared = prepare(
            manifest,
            plan,
            BaseQualificationStudyManifest.Partition.DEVELOPMENT
        );
        List<CandidateResult> results = new ArrayList<>();
        for (BaseQualificationCandidate.Reducer reducer
            : BaseQualificationCandidate.Reducer.values()) {
            for (int threshold : THRESHOLDS) {
                Map<String, ComponentAreas> components = classify(
                    prepared,
                    reducer,
                    threshold
                );
                for (int area : MINIMUM_AREAS) {
                    BaseQualificationCandidate candidate = new BaseQualificationCandidate(
                        reducer,
                        threshold,
                        area
                    );
                    results.add(evaluate(
                        manifest,
                        plan,
                        responses,
                        candidate,
                        prepared,
                        components
                    ));
                }
            }
        }
        CandidateResult selected = results.stream()
            .filter(CandidateResult::isPassed)
            .min(selectionOrder())
            .orElse(null);
        return new FamilyResult(results, selected);
    }

    static CandidateResult evaluateFrozenCandidate(
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan,
        BaseQualificationResponseSet responses,
        BaseQualificationCandidate candidate
    ) {
        requirePartition(responses, BaseQualificationStudyManifest.Partition.HELD_OUT);
        Map<String, PreparedCapture> prepared = prepare(
            manifest,
            plan,
            BaseQualificationStudyManifest.Partition.HELD_OUT
        );
        Map<String, ComponentAreas> components = classify(
            prepared,
            candidate.getReducer(),
            candidate.getThreshold()
        );
        return evaluate(manifest, plan, responses, candidate, prepared, components);
    }

    static ComponentAreas classify(
        BaseQualificationCapture capture,
        BaseQualificationCandidate.Reducer reducer,
        int threshold
    ) {
        return ComponentAreas.create(new PreparedCapture(capture, null), reducer, threshold);
    }

    private static CandidateResult evaluate(
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan,
        BaseQualificationResponseSet responses,
        BaseQualificationCandidate candidate,
        Map<String, PreparedCapture> prepared,
        Map<String, ComponentAreas> components
    ) {
        BaseQualificationStudyManifest.Partition partition = responses.getPartition();
        Map<String, Boolean> qualifiedByTarget = new LinkedHashMap<>();
        int recognizable = 0;
        int retained = 0;
        int unsafe = 0;
        for (BaseQualificationTarget target : plan.getTargets()) {
            if (target.getPair().getPartition() != partition) {
                continue;
            }
            boolean qualified = isTargetQualified(
                target.getMask(),
                requireComponents(components, target.getCapture().getCaptureName()),
                candidate.getMinimumArea()
            );
            qualifiedByTarget.put(target.getTargetId(), qualified);
            boolean targetRecognizable = responses.requireLabel(
                target.getTargetId()
            ).isRecognizable();
            if (targetRecognizable) {
                recognizable++;
                if (qualified) {
                    retained++;
                }
            } else if (qualified) {
                unsafe++;
            }
        }
        int forbiddenPixels = countForbiddenQualifiedPixels(
            prepared,
            components,
            candidate.getMinimumArea()
        );
        TemporalResult temporal = temporalResult(
            manifest,
            plan,
            responses,
            candidate,
            components
        );
        double recall = recognizable == 0 ? 0.0 : retained / (double)recognizable;
        double requiredRecall = partition == BaseQualificationStudyManifest.Partition.DEVELOPMENT
            ? DEVELOPMENT_MINIMUM_RECALL
            : HELD_OUT_MINIMUM_RECALL;
        boolean passed = unsafe == 0
            && recognizable > 0
            && recall >= requiredRecall
            && forbiddenPixels == 0
            && temporal.toggles == 0
            && temporal.maximumBlockLightChange <= MAXIMUM_BLOCK_LIGHT_CHANGE;
        return new CandidateResult(
            candidate,
            qualifiedByTarget,
            recognizable,
            retained,
            unsafe,
            forbiddenPixels,
            temporal.toggles,
            temporal.maximumBlockLightChange,
            passed
        );
    }

    private static Map<String, PreparedCapture> prepare(
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan,
        BaseQualificationStudyManifest.Partition partition
    ) {
        // Partition masking prevents development components from computing held-out outcomes.
        boolean[] allowedClasses = new boolean[0x10000];
        for (BaseQualificationStudyManifest.Pair pair : manifest.getPairs()) {
            if (pair.getPartition() == partition) {
                allowedClasses[pair.getClassA()] = true;
                allowedClasses[pair.getClassB()] = true;
            }
        }
        Map<String, PreparedCapture> prepared = new LinkedHashMap<>();
        for (Map.Entry<String, BaseQualificationCapture> entry : plan.getCaptures().entrySet()) {
            prepared.put(
                entry.getKey(),
                new PreparedCapture(entry.getValue(), allowedClasses)
            );
        }
        return prepared;
    }

    private static Map<String, ComponentAreas> classify(
        Map<String, PreparedCapture> prepared,
        BaseQualificationCandidate.Reducer reducer,
        int threshold
    ) {
        Map<String, ComponentAreas> components = new LinkedHashMap<>();
        for (Map.Entry<String, PreparedCapture> entry : prepared.entrySet()) {
            components.put(
                entry.getKey(),
                ComponentAreas.create(entry.getValue(), reducer, threshold)
            );
        }
        return components;
    }

    private static int countForbiddenQualifiedPixels(
        Map<String, PreparedCapture> prepared,
        Map<String, ComponentAreas> components,
        int minimumArea
    ) {
        int count = 0;
        for (Map.Entry<String, PreparedCapture> entry : prepared.entrySet()) {
            PreparedCapture capture = entry.getValue();
            ComponentAreas areas = requireComponents(components, entry.getKey());
            for (int pixel = 0; pixel < capture.classes.length; pixel++) {
                if (areas.isQualified(pixel, minimumArea)
                    && (capture.reasons[pixel] != BaseInfluenceReasonEncoding.SUPPORTED
                        || capture.exactZero[pixel] != 0
                        || capture.classes[pixel] == 0)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static TemporalResult temporalResult(
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan,
        BaseQualificationResponseSet responses,
        BaseQualificationCandidate candidate,
        Map<String, ComponentAreas> components
    ) {
        int toggles = 0;
        double maximumBlockLightChange = 0.0;
        for (BaseQualificationStudyManifest.Capture representative : manifest.getCaptures()) {
            if (representative.getRole() != BaseQualificationStudyManifest.CaptureRole.TARGET
                || representative.getTemporalGroup() == null) {
                continue;
            }
            List<BaseQualificationStudyManifest.Capture> group = temporalGroup(
                manifest,
                representative.getTemporalGroup()
            );
            if (group.size() != 5) {
                throw new IllegalArgumentException(
                    "Calibration temporal group must contain five frames: "
                        + representative.getTemporalGroup()
                );
            }
            for (BaseQualificationStudyManifest.Pair pair : manifest.getPairs()) {
                if (pair.getPartition() != responses.getPartition()) {
                    continue;
                }
                BaseQualificationTarget target = requireScheduledTarget(
                    plan,
                    representative.getCaptureName(),
                    pair.getCode()
                );
                ComponentAreas referenceAreas = requireComponents(
                    components,
                    representative.getCaptureName()
                );
                boolean referenceQualified = isTargetQualified(
                    target.getMask(),
                    referenceAreas,
                    candidate.getMinimumArea()
                );
                boolean stableLabel = responses.requireLabel(target.getTargetId()).isStable();
                for (BaseQualificationStudyManifest.Capture frame : group) {
                    BaseQualificationTargetMask frameMask = plan.geometryForCapture(
                        frame.getCaptureName()
                    ).requireMask(pair.getCode(), target.getRegion());
                    ComponentAreas frameAreas = requireComponents(
                        components,
                        frame.getCaptureName()
                    );
                    boolean frameQualified = isTargetQualified(
                        frameMask,
                        frameAreas,
                        candidate.getMinimumArea()
                    );
                    if (stableLabel && frameQualified != referenceQualified) {
                        toggles++;
                    }
                    if ("block-light".equals(representative.getTemporalGroup())) {
                        maximumBlockLightChange = Math.max(
                            maximumBlockLightChange,
                            changedInteriorFraction(
                                target.getMask(),
                                referenceAreas,
                                frameMask,
                                frameAreas,
                                candidate.getMinimumArea()
                            )
                        );
                    }
                }
            }
        }
        return new TemporalResult(toggles, maximumBlockLightChange);
    }

    private static double changedInteriorFraction(
        BaseQualificationTargetMask firstMask,
        ComponentAreas first,
        BaseQualificationTargetMask secondMask,
        ComponentAreas second,
        int minimumArea
    ) {
        if (firstMask.getWidth() != secondMask.getWidth()
            || firstMask.getHeight() != secondMask.getHeight()) {
            return 1.0;
        }
        int comparable = 0;
        int changed = 0;
        for (int pixel = 0; pixel < first.size(); pixel++) {
            if (firstMask.contains(pixel) && secondMask.contains(pixel)
                && first.isInterior(pixel) && second.isInterior(pixel)) {
                comparable++;
                if (first.isQualified(pixel, minimumArea)
                    != second.isQualified(pixel, minimumArea)) {
                    changed++;
                }
            }
        }
        return comparable == 0 ? 1.0 : changed / (double)comparable;
    }

    private static boolean isTargetQualified(
        BaseQualificationTargetMask mask,
        ComponentAreas areas,
        int minimumArea
    ) {
        for (int pixel = 0; pixel < areas.size(); pixel++) {
            if (mask.contains(pixel) && areas.isQualified(pixel, minimumArea)) {
                return true;
            }
        }
        return false;
    }

    private static List<BaseQualificationStudyManifest.Capture> temporalGroup(
        BaseQualificationStudyManifest manifest,
        String groupName
    ) {
        List<BaseQualificationStudyManifest.Capture> captures = new ArrayList<>();
        for (BaseQualificationStudyManifest.Capture capture : manifest.getCaptures()) {
            if (groupName.equals(capture.getTemporalGroup())) {
                captures.add(capture);
            }
        }
        captures.sort(Comparator.comparingInt(BaseQualificationStudyManifest.Capture::getRepeatIndex));
        return captures;
    }

    private static BaseQualificationTarget requireScheduledTarget(
        BaseQualificationTrialPlan plan,
        String captureName,
        String pairCode
    ) {
        for (BaseQualificationTarget target : plan.getTargets()) {
            if (target.getCapture().getCaptureName().equals(captureName)
                && target.getPair().getCode().equals(pairCode)) {
                return target;
            }
        }
        throw new IllegalArgumentException("Temporal calibration target is missing");
    }

    private static ComponentAreas requireComponents(
        Map<String, ComponentAreas> components,
        String captureName
    ) {
        ComponentAreas result = components.get(captureName);
        if (result == null) {
            throw new IllegalArgumentException("Calibration classification is missing: " + captureName);
        }
        return result;
    }

    private static Comparator<CandidateResult> selectionOrder() {
        return Comparator.comparingDouble(CandidateResult::getRecall).reversed()
            .thenComparing(
                Comparator.comparingInt(
                    (CandidateResult result) -> result.getCandidate().getThreshold()
                ).reversed()
            )
            .thenComparing(
                Comparator.comparingInt(
                    (CandidateResult result) -> result.getCandidate().getMinimumArea()
                ).reversed()
            )
            .thenComparingInt(
                result -> result.getCandidate().getReducer().getTieBreakOrder()
            );
    }

    private static void requirePartition(
        BaseQualificationResponseSet responses,
        BaseQualificationStudyManifest.Partition expected
    ) {
        if (responses == null || responses.getPartition() != expected) {
            throw new IllegalArgumentException("Calibration response partition is invalid");
        }
    }

    private static final class PreparedCapture {
        private final int width;
        private final int height;
        private final int[] classes;
        private final int[] depthBits;
        private final byte[] reasons;
        private final byte[] exactZero;
        private final byte[] influenceRgb;
        private final boolean[] interior;

        private PreparedCapture(BaseQualificationCapture capture, boolean[] allowedClasses) {
            width = capture.getWidth();
            height = capture.getHeight();
            classes = capture.copyBaseClassIds();
            depthBits = capture.copyBaseDepthBits();
            reasons = capture.getComposition().copyReasons();
            exactZero = capture.copyExactZeroInfluence();
            influenceRgb = capture.getComposition().copyRgb();
            interior = erodedInterior(width, height, classes, depthBits, reasons);
            if (allowedClasses != null) {
                for (int pixel = 0; pixel < classes.length; pixel++) {
                    int classId = classes[pixel];
                    interior[pixel] = interior[pixel]
                        && classId >= 0
                        && classId < allowedClasses.length
                        && allowedClasses[classId];
                }
            }
        }
    }

    static final class ComponentAreas {
        private final boolean[] interior;
        private final int[] areaByPixel;

        private ComponentAreas(boolean[] interior, int[] areaByPixel) {
            this.interior = interior;
            this.areaByPixel = areaByPixel;
        }

        private static ComponentAreas create(
            PreparedCapture capture,
            BaseQualificationCandidate.Reducer reducer,
            int threshold
        ) {
            if (reducer == null || threshold < 0 || threshold > 255) {
                throw new IllegalArgumentException("Calibration classification parameters are invalid");
            }
            int pixels = capture.classes.length;
            boolean[] passing = new boolean[pixels];
            for (int pixel = 0; pixel < pixels; pixel++) {
                int offset = pixel * 3;
                int reduced = reducer.reduce(
                    Byte.toUnsignedInt(capture.influenceRgb[offset]),
                    Byte.toUnsignedInt(capture.influenceRgb[offset + 1]),
                    Byte.toUnsignedInt(capture.influenceRgb[offset + 2])
                );
                passing[pixel] = capture.interior[pixel]
                    && capture.exactZero[pixel] == 0
                    && reduced >= threshold;
            }
            int[] areaByPixel = componentAreas(
                capture.width,
                capture.height,
                capture.classes,
                passing
            );
            return new ComponentAreas(capture.interior.clone(), areaByPixel);
        }

        int size() {
            return areaByPixel.length;
        }

        boolean isInterior(int pixel) {
            return interior[pixel];
        }

        boolean isQualified(int pixel, int minimumArea) {
            return minimumArea > 0 && areaByPixel[pixel] >= minimumArea;
        }

        int componentArea(int pixel) {
            return areaByPixel[pixel];
        }
    }

    private static boolean[] erodedInterior(
        int width,
        int height,
        int[] classes,
        int[] depthBits,
        byte[] reasons
    ) {
        // Eight-neighbor erosion excludes the declared one-pixel class/depth/support boundary.
        boolean[] interior = new boolean[classes.length];
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int pixel = y * width + x;
                if (classes[pixel] <= 0
                    || Byte.toUnsignedInt(reasons[pixel]) != BaseInfluenceReasonEncoding.SUPPORTED) {
                    continue;
                }
                boolean valid = true;
                for (int offsetY = -1; offsetY <= 1 && valid; offsetY++) {
                    for (int offsetX = -1; offsetX <= 1; offsetX++) {
                        int neighbor = (y + offsetY) * width + x + offsetX;
                        if (classes[neighbor] != classes[pixel]
                            || depthBits[neighbor] != depthBits[pixel]
                            || Byte.toUnsignedInt(reasons[neighbor])
                                != BaseInfluenceReasonEncoding.SUPPORTED) {
                            valid = false;
                            break;
                        }
                    }
                }
                interior[pixel] = valid;
            }
        }
        return interior;
    }

    private static int[] componentAreas(
        int width,
        int height,
        int[] classes,
        boolean[] passing
    ) {
        // Components use four-neighbor adjacency and never cross an exact semantic class boundary.
        int[] areaByPixel = new int[passing.length];
        int[] queue = new int[passing.length];
        for (int start = 0; start < passing.length; start++) {
            if (!passing[start] || areaByPixel[start] != 0) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            areaByPixel[start] = -1;
            while (head < tail) {
                int pixel = queue[head++];
                int x = pixel % width;
                int y = pixel / width;
                if (x > 0) {
                    tail = enqueue(pixel - 1, classes[pixel], passing, classes, areaByPixel, queue, tail);
                }
                if (x + 1 < width) {
                    tail = enqueue(pixel + 1, classes[pixel], passing, classes, areaByPixel, queue, tail);
                }
                if (y > 0) {
                    tail = enqueue(pixel - width, classes[pixel], passing, classes, areaByPixel, queue, tail);
                }
                if (y + 1 < height) {
                    tail = enqueue(pixel + width, classes[pixel], passing, classes, areaByPixel, queue, tail);
                }
            }
            for (int index = 0; index < tail; index++) {
                areaByPixel[queue[index]] = tail;
            }
        }
        return areaByPixel;
    }

    private static int enqueue(
        int pixel,
        int classId,
        boolean[] passing,
        int[] classes,
        int[] areaByPixel,
        int[] queue,
        int tail
    ) {
        if (passing[pixel] && classes[pixel] == classId && areaByPixel[pixel] == 0) {
            areaByPixel[pixel] = -1;
            queue[tail++] = pixel;
        }
        return tail;
    }

    private static final class TemporalResult {
        private final int toggles;
        private final double maximumBlockLightChange;

        private TemporalResult(int toggles, double maximumBlockLightChange) {
            this.toggles = toggles;
            this.maximumBlockLightChange = maximumBlockLightChange;
        }
    }
}
