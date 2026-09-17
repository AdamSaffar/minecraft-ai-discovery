package io.github.minecraftaidiscovery.semantic.visibility;

import static org.junit.Assert.assertEquals;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class BaseQualificationStudyManifestTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void frozenMatrixHasSixPairsAndThirtyOneCaptures() throws Exception {
        BaseQualificationTestFixtures.Fixture fixture = BaseQualificationTestFixtures.create(
            temporaryFolder.newFolder("fixture").toPath()
        );
        assertEquals(6, fixture.manifest.getPairs().size());
        assertEquals(31, fixture.manifest.getCaptures().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void fractionalConditionOrdinalIsRejected() throws Exception {
        BaseQualificationTestFixtures.Fixture fixture = BaseQualificationTestFixtures.create(
            temporaryFolder.newFolder("fixture").toPath()
        );
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(
            fixture.manifestPath,
            StandardCharsets.UTF_8
        )) {
            root = new JsonParser().parse(reader).getAsJsonObject();
        }
        root.getAsJsonArray("captures").get(0).getAsJsonObject()
            .addProperty("conditionOrdinal", 0.5);
        Path invalid = temporaryFolder.newFile("invalid.json").toPath();
        Files.write(
            invalid,
            new GsonBuilder().create().toJson(root).getBytes(StandardCharsets.UTF_8)
        );
        BaseQualificationStudyManifest.read(invalid);
    }
}
