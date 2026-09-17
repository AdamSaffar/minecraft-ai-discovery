package io.github.minecraftaidiscovery.semantic.visibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class BaseQualificationCandidateEvaluatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void reducersUseFrozenIntegerDefinitions() {
        assertEquals(10, BaseQualificationCandidate.Reducer.MINIMUM.reduce(10, 80, 200));
        assertEquals(200, BaseQualificationCandidate.Reducer.MAXIMUM.reduce(10, 80, 200));
        assertEquals(
            (54 * 10 + 183 * 80 + 19 * 200 + 128) >> 8,
            BaseQualificationCandidate.Reducer.LUMINANCE.reduce(10, 80, 200)
        );
    }

    @Test
    public void erosionThresholdAreaAndExactZeroAreAppliedInOrder() {
        BaseQualificationCapture capture = BaseQualificationTestFixtures.smallComponentCapture(
            6,
            6,
            64,
            false
        );
        BaseQualificationCandidateEvaluator.ComponentAreas passing =
            BaseQualificationCandidateEvaluator.classify(
                capture,
                BaseQualificationCandidate.Reducer.MINIMUM,
                64
            );
        int center = 102 * SameFrameRgbEncoding.WIDTH + 102;
        int boundary = 100 * SameFrameRgbEncoding.WIDTH + 100;
        assertEquals(16, passing.componentArea(center));
        assertTrue(passing.isQualified(center, 16));
        assertFalse(passing.isQualified(center, 17));
        assertFalse(passing.isInterior(boundary));

        BaseQualificationCandidateEvaluator.ComponentAreas aboveThreshold =
            BaseQualificationCandidateEvaluator.classify(
                capture,
                BaseQualificationCandidate.Reducer.MINIMUM,
                65
            );
        assertEquals(0, aboveThreshold.componentArea(center));

        BaseQualificationCapture zero = BaseQualificationTestFixtures.smallComponentCapture(
            6,
            6,
            255,
            true
        );
        BaseQualificationCandidateEvaluator.ComponentAreas zeroResult =
            BaseQualificationCandidateEvaluator.classify(
                zero,
                BaseQualificationCandidate.Reducer.MAXIMUM,
                1
            );
        assertEquals(0, zeroResult.componentArea(center));
    }

    @Test
    public void frozenCandidatePassesSyntheticHeldOutControls() throws Exception {
        BaseQualificationTestFixtures.Fixture fixture = BaseQualificationTestFixtures.create(
            temporaryFolder.newFolder("fixture").toPath()
        );
        Path responsesPath = BaseQualificationTestFixtures.writeResponses(
            temporaryFolder.newFile("held-out.tsv").toPath(),
            fixture.plan,
            BaseQualificationStudyManifest.Partition.HELD_OUT
        );
        BaseQualificationResponseSet responses = BaseQualificationResponseSet.read(
            responsesPath,
            BaseQualificationStudyManifest.Partition.HELD_OUT,
            fixture.plan
        );
        BaseQualificationCandidateEvaluator.CandidateResult result =
            BaseQualificationCandidateEvaluator.evaluateFrozenCandidate(
                fixture.manifest,
                fixture.plan,
                responses,
                new BaseQualificationCandidate(
                    BaseQualificationCandidate.Reducer.MINIMUM,
                    64,
                    16
                )
            );
        assertTrue(responses.isSessionValid());
        assertTrue(result.isPassed());
        assertEquals(0, result.getUnsafeReveals());
        assertEquals(0, result.getForbiddenQualifiedPixels());
        assertEquals(0, result.getTemporalToggles());
        assertTrue(result.getRecall() > 0.95);
    }
}
