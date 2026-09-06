package io.github.minecraftaidiscovery.semantic.manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.Test;

public class BlockClassManifestTest {
    private static final String MINECRAFT_VERSION = "1.16.1";
    private static final String YARN_MAPPINGS = "1.16.1+build.21";
    private static final String REGISTRY_NAME = "minecraft:block";

    @Test
    public void sortsIdentifiersAndAssignsUniqueContiguousPositiveIds() {
        BlockClassManifest manifest = generate(
            source("minecraft:stone", 1),
            source("minecraft:air", 0),
            source("minecraft:oak_log", 73)
        );

        assertEquals(0, manifest.getBackgroundClassId());
        assertEquals(3, manifest.getEntries().size());
        assertEquals("minecraft:air", manifest.getEntries().get(0).getRegistryIdentifier());
        assertEquals("minecraft:oak_log", manifest.getEntries().get(1).getRegistryIdentifier());
        assertEquals("minecraft:stone", manifest.getEntries().get(2).getRegistryIdentifier());

        Set<Integer> classIds = new HashSet<>();
        for (int index = 0; index < manifest.getEntries().size(); index++) {
            int classId = manifest.getEntries().get(index).getSemanticClassId();
            assertEquals(index + 1, classId);
            assertNotEquals(BlockClassManifest.BACKGROUND_CLASS_ID, classId);
            assertTrue(classIds.add(classId));
        }
    }

    @Test
    public void generatesStableFingerprintFromCanonicalIdentity() {
        BlockClassManifest first = generate(
            source("minecraft:stone", 1),
            source("minecraft:oak_log", 73)
        );
        BlockClassManifest reordered = generate(
            source("minecraft:oak_log", 73),
            source("minecraft:stone", 1)
        );

        assertEquals(first.getFingerprintSha256(), reordered.getFingerprintSha256());
        assertEquals(
            "b1d8416619267130bbc42d0f1eb7b9bcc3ab436b2c03e43b88d27ba11abe11d1",
            first.getFingerprintSha256()
        );
        assertTrue(first.canonicalFingerprintData().endsWith("2\tminecraft:stone\n"));
    }

    @Test
    public void excludesDiagnosticRawIdsFromAssignmentAndFingerprint() {
        BlockClassManifest first = generate(
            source("minecraft:oak_log", 73),
            source("minecraft:stone", 1)
        );
        BlockClassManifest changedRawIds = generate(
            source("minecraft:oak_log", 2),
            source("minecraft:stone", 900)
        );

        assertEquals(first.getFingerprintSha256(), changedRawIds.getFingerprintSha256());
        assertEquals(
            first.getEntries().get(0).getSemanticClassId(),
            changedRawIds.getEntries().get(0).getSemanticClassId()
        );
        assertNotEquals(
            first.getEntries().get(0).getDiagnosticRawId(),
            changedRawIds.getEntries().get(0).getDiagnosticRawId()
        );
    }

    @Test
    public void writesCandidateMetadataAndDiagnosticRawIds() {
        BlockClassManifest manifest = generate(
            source("minecraft:air", 0),
            new BlockClassManifest.SourceEntry("minecraft:stone", null)
        );
        String json = BlockClassManifestJson.toJson(manifest);

        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"candidate\": true"));
        assertTrue(json.contains("\"entryCount\": 2"));
        assertTrue(json.contains("\"registry\": \"minecraft:block\""));
        assertTrue(json.contains("\"diagnosticRawId\": 0"));
        assertEquals(1, occurrences(json, "\"diagnosticRawId\""));
        assertTrue(json.indexOf("minecraft:air") < json.indexOf("minecraft:stone"));
        assertTrue(json.contains(BlockClassManifest.FINGERPRINT_CANONICAL_DATA_FORMAT.replace("\\", "\\\\")));
    }

    @Test
    public void rejectsDuplicateRegistryIdentifiers() {
        expectIllegalArgument(
            () -> generate(source("minecraft:stone", 1), source("minecraft:stone", 2)),
            "Duplicate block registry identifier"
        );
    }

    @Test
    public void rejectsMissingRegistryData() {
        expectIllegalArgument(
            () -> generate(new BlockClassManifest.SourceEntry(null, 1)),
            "block registry identifier is missing"
        );
        expectIllegalArgument(
            () -> BlockClassManifest.generate(MINECRAFT_VERSION, YARN_MAPPINGS, REGISTRY_NAME, null),
            "Registry entries are missing"
        );
        expectIllegalArgument(
            () -> generate((BlockClassManifest.SourceEntry)null),
            "Registry entry is missing"
        );
    }

    @Test
    public void rejectsMalformedRegistryIdentifiersAndRawIds() {
        for (String identifier : Arrays.asList("stone", "Minecraft:stone", "minecraft:", ":stone")) {
            expectIllegalArgument(
                () -> generate(source(identifier, 1)),
                "Malformed block registry identifier"
            );
        }
        expectIllegalArgument(
            () -> generate(source("minecraft:stone", -1)),
            "Diagnostic raw ID must be nonnegative"
        );
    }

    @Test
    public void rejectsRegistryLargerThanUnsignedShortRange() {
        List<BlockClassManifest.SourceEntry> entries = new ArrayList<>();
        for (int index = 0; index <= BlockClassManifest.MAX_SEMANTIC_CLASS_ID; index++) {
            entries.add(source(String.format(Locale.ROOT, "test:block_%05d", index), index));
        }

        expectIllegalArgument(
            () -> BlockClassManifest.generate(MINECRAFT_VERSION, YARN_MAPPINGS, REGISTRY_NAME, entries),
            "unsigned 16-bit semantic class range"
        );
    }

    @Test
    public void rejectsMismatchedExpectedRegistryCount() {
        BlockClassManifest manifest = generate(source("minecraft:air", 0));

        try {
            manifest.requireEntryCount(763);
            fail("Expected registry count mismatch to fail");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("Expected 763 block registry entries but found 1"));
        }
    }

    @Test
    public void rejectsMismatchedExpectedFingerprint() {
        BlockClassManifest manifest = generate(source("minecraft:air", 0));

        try {
            manifest.requireFingerprint(
                "0000000000000000000000000000000000000000000000000000000000000000"
            );
            fail("Expected manifest fingerprint mismatch to fail");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("Expected block class manifest SHA-256"));
        }
    }

    private static BlockClassManifest generate(BlockClassManifest.SourceEntry... entries) {
        return BlockClassManifest.generate(
            MINECRAFT_VERSION,
            YARN_MAPPINGS,
            REGISTRY_NAME,
            Arrays.asList(entries)
        );
    }

    private static BlockClassManifest.SourceEntry source(String identifier, Integer rawId) {
        return new BlockClassManifest.SourceEntry(identifier, rawId);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static void expectIllegalArgument(ThrowingRunnable runnable, String expectedMessage) {
        try {
            runnable.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains(expectedMessage));
        }
    }

    private interface ThrowingRunnable {
        void run();
    }
}
