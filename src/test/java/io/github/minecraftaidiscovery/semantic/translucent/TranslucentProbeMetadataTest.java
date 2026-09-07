package io.github.minecraftaidiscovery.semantic.translucent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.minecraftaidiscovery.semantic.SemanticCaptureMetadata;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassVocabulary;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class TranslucentProbeMetadataTest {
    @Test
    public void marksProbeArtifactsAsResearcherOnlyAndKeepsV3Unchanged() {
        BlockClassVocabulary vocabulary = vocabulary();
        Map<String, TranslucentProbeMetadata.Artifact> artifacts = new LinkedHashMap<>();
        artifacts.put(
            "baseClasses",
            new TranslucentProbeMetadata.Artifact(
                "base.classes.u16le",
                "uint16 little-endian class id",
                2,
                640 * 360 * 2
            )
        );
        Map<Integer, Long> observed = new LinkedHashMap<>();
        observed.put(vocabulary.requireClassId("minecraft:water"), 12L);

        String json = TranslucentProbeMetadata.toJson(
            640,
            360,
            "fancy",
            "minecraft:water",
            Arrays.asList("vanilla"),
            vocabulary,
            artifacts,
            "base.png",
            "translucent-frontmost.png",
            3,
            1,
            observed
        );

        assertTrue(json.contains("\"format\": \"translucent-observation-probe-v1\""));
        assertTrue(json.contains("\"experimental\": true"));
        assertTrue(json.contains("\"researcherOnly\": true"));
        assertTrue(json.contains("\"agentVisible\": false"));
        assertTrue(json.contains("\"productionObservationContract\": false"));
        assertTrue(json.contains("\"format\": \"" + SemanticCaptureMetadata.FORMAT_NAME + "\""));
        assertTrue(json.contains("\"unchanged\": true"));
        assertTrue(json.contains("\"productionK\": false"));
        assertTrue(json.contains("\"minecraft:water\": 3"));
        assertTrue(json.contains("\"minecraft:lava\": 2"));
        assertTrue(json.contains(vocabulary.getFingerprintSha256()));
        assertFalse(json.contains("diagnosticRawId"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsProductionSizedOutputInTheProbeSchema() {
        TranslucentProbeMetadata.toJson(
            1920,
            1080,
            "fast",
            "none",
            Arrays.asList("vanilla"),
            vocabulary(),
            new LinkedHashMap<>(),
            "base.png",
            "front.png",
            0,
            0,
            new LinkedHashMap<>()
        );
    }

    private static BlockClassVocabulary vocabulary() {
        return new BlockClassVocabulary(
            BlockClassManifest.generate(
                "1.16.1",
                "1.16.1+build.21",
                "minecraft:block",
                Arrays.asList(
                    new BlockClassManifest.SourceEntry("minecraft:ice", 79),
                    new BlockClassManifest.SourceEntry("minecraft:lava", 27),
                    new BlockClassManifest.SourceEntry("minecraft:water", 26)
                )
            )
        );
    }
}
