package io.github.minecraftaidiscovery.semantic.visibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class BaseQualificationAnalysisFilesTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void developmentFreezeGatesHeldOutEvaluation() throws Exception {
        BaseQualificationTestFixtures.Fixture fixture = BaseQualificationTestFixtures.create(
            temporaryFolder.newFolder("fixture").toPath()
        );
        Path study = temporaryFolder.getRoot().toPath().resolve("study");
        BaseQualificationTrialWriter.write(
            study,
            fixture.manifestPath,
            fixture.manifest,
            fixture.plan
        );
        Path developmentResponses = BaseQualificationTestFixtures.writeResponses(
            temporaryFolder.newFile("development.tsv").toPath(),
            fixture.plan,
            BaseQualificationStudyManifest.Partition.DEVELOPMENT
        );
        BaseQualificationAnalysisFiles.freezeDevelopment(
            fixture.manifestPath,
            study,
            developmentResponses,
            fixture.manifest,
            fixture.plan
        );
        JsonObject freeze = readObject(study.resolve("development-freeze.json"));
        assertEquals("frozen_candidate", freeze.get("status").getAsString());
        assertTrue(freeze.has("candidate"));
        assertTrue(Files.isDirectory(study.resolve("labeler-held-out")));
        assertTrue(!Files.exists(study.resolve("sealed/labeler-held-out")));

        Path heldOutResponses = BaseQualificationTestFixtures.writeResponses(
            temporaryFolder.newFile("held-out.tsv").toPath(),
            fixture.plan,
            BaseQualificationStudyManifest.Partition.HELD_OUT
        );
        BaseQualificationAnalysisFiles.evaluateHeldOut(
            fixture.manifestPath,
            study,
            heldOutResponses,
            fixture.manifest,
            fixture.plan
        );
        JsonObject heldOut = readObject(study.resolve("held-out-analysis.json"));
        assertTrue(
            heldOut.getAsJsonObject("frozenCandidateResult").get("passed").getAsBoolean()
        );
        assertTrue(heldOut.has("developmentFreezeSha256"));
    }

    @Test
    public void heldOutKeyIsNotOpenedBeforeDevelopmentFreezeExists() throws Exception {
        BaseQualificationTestFixtures.Fixture fixture = BaseQualificationTestFixtures.create(
            temporaryFolder.newFolder("fixture").toPath()
        );
        Path study = temporaryFolder.getRoot().toPath().resolve("study");
        BaseQualificationTrialWriter.write(
            study,
            fixture.manifestPath,
            fixture.manifest,
            fixture.plan
        );
        Files.delete(study.resolve("sealed/held-out-key.json"));
        Path heldOutResponses = BaseQualificationTestFixtures.writeResponses(
            temporaryFolder.newFile("held-out.tsv").toPath(),
            fixture.plan,
            BaseQualificationStudyManifest.Partition.HELD_OUT
        );
        try {
            BaseQualificationAnalysisFiles.evaluateHeldOut(
                fixture.manifestPath,
                study,
                heldOutResponses,
                fixture.manifest,
                fixture.plan
            );
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("development-freeze.json"));
            return;
        }
        throw new AssertionError("Held-out evaluation should require a development freeze");
    }

    private static JsonObject readObject(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }
}
