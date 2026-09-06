package io.github.minecraftaidiscovery.semantic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassVocabulary;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class SemanticCaptureMetadataTest {
    @Test
    public void includesManifestFingerprintAndOnlyObservedPositiveClasses() {
        BlockClassVocabulary vocabulary = vocabulary();
        Map<Integer, Long> counts = new LinkedHashMap<>();
        counts.put(0, 2L);
        counts.put(1, 1L);

        String json = SemanticCaptureMetadata.toJson(
            3,
            1,
            "capture.classes.u16le",
            6,
            "capture.png",
            vocabulary,
            counts
        );

        assertTrue(json.contains("\"formatVersion\": 2"));
        assertTrue(json.contains("\"encoding\": \"uint16 little-endian class id\""));
        assertTrue(json.contains("\"byteLength\": 6"));
        assertTrue(json.contains(vocabulary.getFingerprintSha256()));
        assertTrue(json.contains("\"registryIdentifier\": \"minecraft:oak_log\""));
        assertFalse(json.contains("\"registryIdentifier\": \"minecraft:stone\""));
        assertFalse(json.contains("diagnosticRawId"));
    }

    @Test
    public void rejectsUnknownClassesAndMismatchedBufferState() {
        BlockClassVocabulary vocabulary = vocabulary();
        Map<Integer, Long> unknown = new LinkedHashMap<>();
        unknown.put(700, 1L);

        try {
            SemanticCaptureMetadata.toJson(
                1,
                1,
                "capture.classes.u16le",
                2,
                "capture.png",
                vocabulary,
                unknown
            );
            fail("Expected unknown semantic class to fail");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("absent from the active block manifest"));
        }

        Map<Integer, Long> background = new LinkedHashMap<>();
        background.put(0, 1L);
        try {
            SemanticCaptureMetadata.toJson(
                1,
                1,
                "capture.classes.u16le",
                1,
                "capture.png",
                vocabulary,
                background
            );
            fail("Expected mismatched buffer length to fail");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("Expected class-buffer length 2"));
        }
    }

    private static BlockClassVocabulary vocabulary() {
        return new BlockClassVocabulary(
            BlockClassManifest.generate(
                "1.16.1",
                "1.16.1+build.21",
                "minecraft:block",
                Arrays.asList(
                    new BlockClassManifest.SourceEntry("minecraft:oak_log", 73),
                    new BlockClassManifest.SourceEntry("minecraft:stone", 1)
                )
            )
        );
    }
}
