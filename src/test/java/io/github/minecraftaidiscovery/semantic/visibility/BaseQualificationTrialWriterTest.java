package io.github.minecraftaidiscovery.semantic.visibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class BaseQualificationTrialWriterTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writerSeparatesOpaqueLabelerFromSealedKeys() throws Exception {
        BaseQualificationTestFixtures.Fixture fixture = BaseQualificationTestFixtures.create(
            temporaryFolder.newFolder("fixture").toPath()
        );
        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        BaseQualificationTrialWriter.write(
            output,
            fixture.manifestPath,
            fixture.manifest,
            fixture.plan
        );
        Path images = output.resolve("labeler-development/presentations");
        Path heldOutImages = output.resolve("sealed/labeler-held-out/presentations");
        assertEquals(225L, Files.list(images).filter(Files::isRegularFile).count());
        assertEquals(225L, Files.list(heldOutImages).filter(Files::isRegularFile).count());
        Path firstImage = Files.list(images).findFirst().get();
        BufferedImage image = ImageIO.read(firstImage.toFile());
        assertNotNull(image);
        assertEquals(600, image.getWidth());
        assertEquals(240, image.getHeight());

        String html = new String(
            Files.readAllBytes(output.resolve("labeler-development/index.html")),
            StandardCharsets.UTF_8
        );
        assertFalse(html.contains("probe-"));
        assertFalse(html.contains("minecraft:"));
        assertFalse(html.contains("condition"));
        assertFalse(html.contains("held_out"));
        assertTrue(html.contains("Uncertain"));
        assertTrue(html.contains("development-responses.tsv"));
        assertFalse(Files.exists(output.resolve("labeler-held-out")));

        JsonObject development = readObject(output.resolve("sealed/development-key.json"));
        JsonObject heldOut = readObject(output.resolve("sealed/held-out-key.json"));
        assertEquals(225, development.getAsJsonArray("presentations").size());
        assertEquals(225, heldOut.getAsJsonArray("presentations").size());
        assertTrue(Files.isRegularFile(output.resolve("input-lock.json")));
    }

    private static JsonObject readObject(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }
}
