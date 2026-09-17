package io.github.minecraftaidiscovery.semantic.visibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class BaseQualificationTrialPlanTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void frozenScheduleProducesThreeOpaquePresentationsPerTarget() throws Exception {
        BaseQualificationTestFixtures.Fixture fixture = BaseQualificationTestFixtures.create(
            temporaryFolder.newFolder("fixture").toPath()
        );
        assertEquals(150, fixture.plan.getTargets().size());
        assertEquals(450, fixture.plan.getPresentations().size());

        Set<String> targetIds = new HashSet<>();
        int positiveControls = 0;
        int negativeControls = 0;
        for (BaseQualificationTarget target : fixture.plan.getTargets()) {
            assertTrue(targetIds.add(target.getTargetId()));
            assertFalse(target.getTargetId().contains(target.getCapture().getCaptureName()));
            if (target.getControl() == BaseQualificationTarget.Control.POSITIVE_CATCH) {
                positiveControls++;
            } else if (target.getControl() == BaseQualificationTarget.Control.NEGATIVE_BLINDING) {
                negativeControls++;
            }
        }
        assertEquals(12, positiveControls);
        assertEquals(12, negativeControls);

        Map<String, Set<Integer>> batches = new HashMap<>();
        Set<String> trialIds = new HashSet<>();
        int development = 0;
        int heldOut = 0;
        for (BaseQualificationPresentation presentation : fixture.plan.getPresentations()) {
            assertTrue(trialIds.add(presentation.getTrialId()));
            batches.computeIfAbsent(
                presentation.getTarget().getTargetId(),
                ignored -> new HashSet<>()
            ).add(presentation.getBatch());
            if (presentation.getTarget().getPair().getPartition()
                == BaseQualificationStudyManifest.Partition.DEVELOPMENT) {
                development++;
            } else {
                heldOut++;
            }
        }
        assertEquals(225, development);
        assertEquals(225, heldOut);
        assertEquals(150, batches.size());
        for (Set<Integer> targetBatches : batches.values()) {
            assertEquals(3, targetBatches.size());
        }
    }
}
