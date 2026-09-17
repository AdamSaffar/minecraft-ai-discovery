package io.github.minecraftaidiscovery.semantic.visibility;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BaseQualificationResponseSet {
    enum Outcome {
        CORRECT,
        INCORRECT,
        UNCERTAIN
    }

    static final class TargetLabel {
        private final boolean recognizable;
        private final boolean stable;
        private final List<Outcome> outcomes;

        private TargetLabel(boolean recognizable, boolean stable, List<Outcome> outcomes) {
            this.recognizable = recognizable;
            this.stable = stable;
            this.outcomes = Collections.unmodifiableList(new ArrayList<>(outcomes));
        }

        boolean isRecognizable() {
            return recognizable;
        }

        boolean isStable() {
            return stable;
        }

        List<Outcome> getOutcomes() {
            return outcomes;
        }
    }

    private final BaseQualificationStudyManifest.Partition partition;
    private final Map<String, BaseQualificationPresentation.Response> responses;
    private final Map<String, TargetLabel> labelsByTarget;
    private final int positiveCatchCorrect;
    private final int positiveCatchTotal;
    private final int negativeControlCorrect;
    private final int negativeControlTotal;

    private BaseQualificationResponseSet(
        BaseQualificationStudyManifest.Partition partition,
        Map<String, BaseQualificationPresentation.Response> responses,
        Map<String, TargetLabel> labelsByTarget,
        int positiveCatchCorrect,
        int positiveCatchTotal,
        int negativeControlCorrect,
        int negativeControlTotal
    ) {
        this.partition = partition;
        this.responses = Collections.unmodifiableMap(new LinkedHashMap<>(responses));
        this.labelsByTarget = Collections.unmodifiableMap(new LinkedHashMap<>(labelsByTarget));
        this.positiveCatchCorrect = positiveCatchCorrect;
        this.positiveCatchTotal = positiveCatchTotal;
        this.negativeControlCorrect = negativeControlCorrect;
        this.negativeControlTotal = negativeControlTotal;
    }

    static BaseQualificationResponseSet read(
        Path responsePath,
        BaseQualificationStudyManifest.Partition partition,
        BaseQualificationTrialPlan plan
    ) throws IOException {
        if (responsePath == null || partition == null || plan == null) {
            throw new IllegalArgumentException("Calibration response input is incomplete");
        }
        List<String> lines = Files.readAllLines(responsePath, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !"trial_id\tresponse".equals(lines.get(0))) {
            throw new IllegalArgumentException("Calibration response header is invalid");
        }
        Map<String, BaseQualificationPresentation> expected = new LinkedHashMap<>();
        for (BaseQualificationPresentation presentation : plan.getPresentations()) {
            if (presentation.getTarget().getPair().getPartition() == partition) {
                expected.put(presentation.getTrialId(), presentation);
            }
        }
        Map<String, BaseQualificationPresentation.Response> responses = new LinkedHashMap<>();
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);
            if (line.isEmpty()) {
                continue;
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length != 2 || !expected.containsKey(fields[0])) {
                throw new IllegalArgumentException(
                    "Calibration response line is unknown or malformed: " + (lineNumber + 1)
                );
            }
            BaseQualificationPresentation.Response response = parseResponse(fields[1]);
            if (responses.put(fields[0], response) != null) {
                throw new IllegalArgumentException(
                    "Calibration response repeats trial " + fields[0]
                );
            }
        }
        if (!responses.keySet().equals(expected.keySet())) {
            Set<String> missing = new HashSet<>(expected.keySet());
            missing.removeAll(responses.keySet());
            throw new IllegalArgumentException(
                "Calibration responses are incomplete; missing " + missing.size() + " trials"
            );
        }

        Map<String, List<Outcome>> outcomesByTarget = new LinkedHashMap<>();
        int positiveCorrect = 0;
        int positiveTotal = 0;
        int negativeCorrect = 0;
        int negativeTotal = 0;
        for (Map.Entry<String, BaseQualificationPresentation> entry : expected.entrySet()) {
            BaseQualificationPresentation presentation = entry.getValue();
            BaseQualificationPresentation.Response response = responses.get(entry.getKey());
            Outcome outcome = outcome(presentation, response);
            outcomesByTarget.computeIfAbsent(
                presentation.getTarget().getTargetId(),
                ignored -> new ArrayList<>()
            ).add(outcome);
            if (presentation.getTarget().getControl()
                == BaseQualificationTarget.Control.POSITIVE_CATCH) {
                positiveTotal++;
                if (outcome == Outcome.CORRECT) {
                    positiveCorrect++;
                }
            } else if (presentation.getTarget().getControl()
                == BaseQualificationTarget.Control.NEGATIVE_BLINDING) {
                negativeTotal++;
                if (outcome == Outcome.CORRECT) {
                    negativeCorrect++;
                }
            }
        }
        Map<String, TargetLabel> labels = new LinkedHashMap<>();
        for (Map.Entry<String, List<Outcome>> entry : outcomesByTarget.entrySet()) {
            List<Outcome> outcomes = entry.getValue();
            if (outcomes.size() != 3) {
                throw new IllegalArgumentException("Every calibration target needs three responses");
            }
            // A target is safe only when all three independently randomized presentations agree.
            boolean recognizable = true;
            boolean stable = true;
            for (Outcome outcome : outcomes) {
                recognizable &= outcome == Outcome.CORRECT;
                stable &= outcome == outcomes.get(0);
            }
            labels.put(entry.getKey(), new TargetLabel(recognizable, stable, outcomes));
        }
        return new BaseQualificationResponseSet(
            partition,
            responses,
            labels,
            positiveCorrect,
            positiveTotal,
            negativeCorrect,
            negativeTotal
        );
    }

    BaseQualificationStudyManifest.Partition getPartition() {
        return partition;
    }

    TargetLabel requireLabel(String targetId) {
        TargetLabel label = labelsByTarget.get(targetId);
        if (label == null) {
            throw new IllegalArgumentException("Calibration target label is missing: " + targetId);
        }
        return label;
    }

    int getResponseCount() {
        return responses.size();
    }

    int getPositiveCatchCorrect() {
        return positiveCatchCorrect;
    }

    int getPositiveCatchTotal() {
        return positiveCatchTotal;
    }

    int getNegativeControlCorrect() {
        return negativeControlCorrect;
    }

    int getNegativeControlTotal() {
        return negativeControlTotal;
    }

    double getNegativeControlTwoSidedPValue() {
        return twoSidedAboveChancePValue(negativeControlCorrect, negativeControlTotal);
    }

    boolean isSessionValid() {
        boolean positiveValid = positiveCatchTotal > 0
            && positiveCatchCorrect / (double)positiveCatchTotal >= 0.95;
        boolean negativeLeak = negativeControlCorrect > negativeControlTotal / 2.0
            && getNegativeControlTwoSidedPValue() < 0.05;
        return positiveValid && !negativeLeak;
    }

    static double twoSidedAboveChancePValue(int correct, int total) {
        if (correct < 0 || total < 0 || correct > total) {
            throw new IllegalArgumentException("Binomial counts are invalid");
        }
        if (total == 0 || correct <= total / 2.0) {
            return 1.0;
        }
        double probability = 0.0;
        for (int successes = correct; successes <= total; successes++) {
            probability += binomialCoefficient(total, successes) * Math.pow(0.5, total);
        }
        return Math.min(1.0, probability * 2.0);
    }

    private static long binomialCoefficient(int total, int selected) {
        int smaller = Math.min(selected, total - selected);
        long result = 1;
        for (int index = 1; index <= smaller; index++) {
            result = result * (total - smaller + index) / index;
        }
        return result;
    }

    private static Outcome outcome(
        BaseQualificationPresentation presentation,
        BaseQualificationPresentation.Response response
    ) {
        if (response == BaseQualificationPresentation.Response.UNCERTAIN) {
            return Outcome.UNCERTAIN;
        }
        return response == presentation.getCorrectResponse()
            ? Outcome.CORRECT
            : Outcome.INCORRECT;
    }

    private static BaseQualificationPresentation.Response parseResponse(String value) {
        if ("left".equals(value)) {
            return BaseQualificationPresentation.Response.LEFT;
        }
        if ("right".equals(value)) {
            return BaseQualificationPresentation.Response.RIGHT;
        }
        if ("uncertain".equals(value)) {
            return BaseQualificationPresentation.Response.UNCERTAIN;
        }
        throw new IllegalArgumentException("Unknown calibration response: " + value);
    }
}
