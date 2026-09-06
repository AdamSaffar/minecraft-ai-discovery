package io.github.minecraftaidiscovery.semantic.manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import org.junit.Test;

public class BlockClassVocabularyTest {
    @Test
    public void mapsCanonicalIdentifiersWithoutUsingDiagnosticRawIds() {
        BlockClassManifest manifest = BlockClassManifest.generate(
            "1.16.1",
            "1.16.1+build.21",
            "minecraft:block",
            Arrays.asList(
                new BlockClassManifest.SourceEntry("minecraft:stone", 1),
                new BlockClassManifest.SourceEntry("minecraft:air", 500),
                new BlockClassManifest.SourceEntry("minecraft:oak_log", 73)
            )
        );
        BlockClassVocabulary vocabulary = new BlockClassVocabulary(manifest);

        assertEquals(1, vocabulary.requireClassId("minecraft:air"));
        assertEquals(2, vocabulary.requireClassId("minecraft:oak_log"));
        assertEquals(3, vocabulary.requireClassId("minecraft:stone"));
        assertEquals("minecraft:oak_log", vocabulary.requireRegistryIdentifier(2));
        assertNotEquals(
            manifest.getEntries().get(0).getDiagnosticRawId().intValue(),
            vocabulary.requireClassId("minecraft:air")
        );
    }

    @Test
    public void failsForUnknownCanonicalIdentityOrClassId() {
        BlockClassManifest manifest = BlockClassManifest.generate(
            "1.16.1",
            "1.16.1+build.21",
            "minecraft:block",
            Arrays.asList(new BlockClassManifest.SourceEntry("minecraft:stone", 1))
        );
        BlockClassVocabulary vocabulary = new BlockClassVocabulary(manifest);

        try {
            vocabulary.requireClassId("minecraft:missing");
            fail("Expected unknown registry identifier to fail");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("absent from the active semantic manifest"));
        }
        try {
            vocabulary.requireRegistryIdentifier(500);
            fail("Expected unknown semantic class ID to fail");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("absent from the active block manifest"));
        }
    }
}
