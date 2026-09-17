package io.github.minecraftaidiscovery.semantic.visibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class BaseQualificationResponseSetTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void completePartitionResponsesProduceValidControls() throws Exception {
        BaseQualificationTestFixtures.Fixture fixture = BaseQualificationTestFixtures.create(
            temporaryFolder.newFolder("fixture").toPath()
        );
        Path responsePath = BaseQualificationTestFixtures.writeResponses(
            temporaryFolder.newFile("development.tsv").toPath(),
            fixture.plan,
            BaseQualificationStudyManifest.Partition.DEVELOPMENT
        );
        BaseQualificationResponseSet responses = BaseQualificationResponseSet.read(
            responsePath,
            BaseQualificationStudyManifest.Partition.DEVELOPMENT,
            fixture.plan
        );
        assertEquals(225, responses.getResponseCount());
        assertEquals(18, responses.getPositiveCatchCorrect());
        assertEquals(18, responses.getPositiveCatchTotal());
        assertEquals(0, responses.getNegativeControlCorrect());
        assertEquals(18, responses.getNegativeControlTotal());
        assertTrue(responses.isSessionValid());
    }

    @Test(expected = IllegalArgumentException.class)
    public void responseFromOtherPartitionIsRejected() throws Exception {
        BaseQualificationTestFixtures.Fixture fixture = BaseQualificationTestFixtures.create(
            temporaryFolder.newFolder("fixture").toPath()
        );
        Path responsePath = BaseQualificationTestFixtures.writeResponses(
            temporaryFolder.newFile("development.tsv").toPath(),
            fixture.plan,
            BaseQualificationStudyManifest.Partition.DEVELOPMENT
        );
        BaseQualificationPresentation heldOut = fixture.plan.getPresentations().stream()
            .filter(presentation -> presentation.getTarget().getPair().getPartition()
                == BaseQualificationStudyManifest.Partition.HELD_OUT)
            .findFirst()
            .get();
        Files.write(
            responsePath,
            (heldOut.getTrialId() + "\tleft\n").getBytes(java.nio.charset.StandardCharsets.UTF_8),
            StandardOpenOption.APPEND
        );
        BaseQualificationResponseSet.read(
            responsePath,
            BaseQualificationStudyManifest.Partition.DEVELOPMENT,
            fixture.plan
        );
    }

    @Test
    public void binomialLeakageTestIsTwoSidedAboveChance() {
        assertEquals(1.0, BaseQualificationResponseSet.twoSidedAboveChancePValue(9, 18), 0.0);
        assertTrue(BaseQualificationResponseSet.twoSidedAboveChancePValue(18, 18) < 0.05);
    }
}
