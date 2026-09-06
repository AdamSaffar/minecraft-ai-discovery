package io.github.minecraftaidiscovery.semantic.manifest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class BlockClassManifest {
    public static final int SCHEMA_VERSION = 1;
    public static final int BACKGROUND_CLASS_ID = 0;
    public static final int MAX_SEMANTIC_CLASS_ID = 0xFFFF;
    public static final String FINGERPRINT_ALGORITHM = "SHA-256";
    public static final String FINGERPRINT_CANONICAL_DATA_FORMAT =
        "UTF-8 bytes with LF line endings and a final LF: "
            + "semantic-block-class-manifest-v1\\n; then <field>\\t<value>\\n for schemaVersion, "
            + "minecraftVersion, yarnMappings, registry, backgroundClassId, and entryCount in that order; "
            + "then <semanticClassId>\\t<registryIdentifier>\\n for each entry in manifest order; "
            + "diagnosticRawId is excluded";

    private static final String FINGERPRINT_HEADER = "semantic-block-class-manifest-v1";
    private static final Pattern CANONICAL_IDENTIFIER = Pattern.compile(
        "^[a-z0-9_.-]+:[a-z0-9/._-]+$"
    );

    private final String minecraftVersion;
    private final String yarnMappings;
    private final String registryName;
    private final List<Entry> entries;
    private final String fingerprintSha256;

    private BlockClassManifest(
        String minecraftVersion,
        String yarnMappings,
        String registryName,
        List<Entry> entries
    ) {
        this.minecraftVersion = minecraftVersion;
        this.yarnMappings = yarnMappings;
        this.registryName = registryName;
        this.entries = Collections.unmodifiableList(entries);
        this.fingerprintSha256 = sha256Hex(canonicalFingerprintData());
    }

    public static BlockClassManifest generate(
        String minecraftVersion,
        String yarnMappings,
        String registryName,
        List<SourceEntry> sourceEntries
    ) {
        validateMetadata("minecraftVersion", minecraftVersion);
        validateMetadata("yarnMappings", yarnMappings);
        validateCanonicalIdentifier("registry", registryName);
        if (sourceEntries == null) {
            throw new IllegalArgumentException("Registry entries are missing");
        }

        List<SourceEntry> sortedEntries = new ArrayList<>(sourceEntries.size());
        Set<String> registryIdentifiers = new HashSet<>();
        for (SourceEntry sourceEntry : sourceEntries) {
            if (sourceEntry == null) {
                throw new IllegalArgumentException("Registry entry is missing");
            }
            String registryIdentifier = sourceEntry.getRegistryIdentifier();
            validateCanonicalIdentifier("block registry identifier", registryIdentifier);
            if (!registryIdentifiers.add(registryIdentifier)) {
                throw new IllegalArgumentException("Duplicate block registry identifier: " + registryIdentifier);
            }
            Integer diagnosticRawId = sourceEntry.getDiagnosticRawId();
            if (diagnosticRawId != null && diagnosticRawId < 0) {
                throw new IllegalArgumentException(
                    "Diagnostic raw ID must be nonnegative for " + registryIdentifier
                );
            }
            sortedEntries.add(sourceEntry);
        }

        if (sortedEntries.size() > MAX_SEMANTIC_CLASS_ID) {
            throw new IllegalArgumentException(
                "Registry has " + sortedEntries.size()
                    + " entries, exceeding the unsigned 16-bit semantic class range"
            );
        }

        sortedEntries.sort(Comparator.comparing(SourceEntry::getRegistryIdentifier));
        List<Entry> assignedEntries = new ArrayList<>(sortedEntries.size());
        for (int index = 0; index < sortedEntries.size(); index++) {
            SourceEntry sourceEntry = sortedEntries.get(index);
            assignedEntries.add(
                new Entry(index + 1, sourceEntry.getRegistryIdentifier(), sourceEntry.getDiagnosticRawId())
            );
        }
        validateAssignments(assignedEntries);
        return new BlockClassManifest(minecraftVersion, yarnMappings, registryName, assignedEntries);
    }

    public int getSchemaVersion() {
        return SCHEMA_VERSION;
    }

    public int getBackgroundClassId() {
        return BACKGROUND_CLASS_ID;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public String getYarnMappings() {
        return yarnMappings;
    }

    public String getRegistryName() {
        return registryName;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public String getFingerprintSha256() {
        return fingerprintSha256;
    }

    public void requireEntryCount(int expectedEntryCount) {
        if (expectedEntryCount < 0) {
            throw new IllegalArgumentException("Expected entry count must be nonnegative");
        }
        if (entries.size() != expectedEntryCount) {
            throw new IllegalStateException(
                "Expected " + expectedEntryCount + " block registry entries but found " + entries.size()
            );
        }
    }

    public String canonicalFingerprintData() {
        StringBuilder canonical = new StringBuilder();
        canonical.append(FINGERPRINT_HEADER).append('\n');
        canonical.append("schemaVersion\t").append(SCHEMA_VERSION).append('\n');
        canonical.append("minecraftVersion\t").append(minecraftVersion).append('\n');
        canonical.append("yarnMappings\t").append(yarnMappings).append('\n');
        canonical.append("registry\t").append(registryName).append('\n');
        canonical.append("backgroundClassId\t").append(BACKGROUND_CLASS_ID).append('\n');
        canonical.append("entryCount\t").append(entries.size()).append('\n');
        for (Entry entry : entries) {
            canonical.append(entry.getSemanticClassId())
                .append('\t')
                .append(entry.getRegistryIdentifier())
                .append('\n');
        }
        return canonical.toString();
    }

    private static void validateMetadata(String name, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " is missing");
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\t') >= 0) {
            throw new IllegalArgumentException(name + " contains a control delimiter");
        }
    }

    private static void validateCanonicalIdentifier(String name, String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException(name + " is missing");
        }
        if (!CANONICAL_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Malformed " + name + ": " + identifier);
        }
    }

    private static void validateAssignments(List<Entry> assignedEntries) {
        Set<Integer> classIds = new HashSet<>();
        int expectedClassId = 1;
        for (Entry entry : assignedEntries) {
            int classId = entry.getSemanticClassId();
            if (classId != expectedClassId) {
                throw new IllegalStateException(
                    "Expected semantic class ID " + expectedClassId + " but found " + classId
                );
            }
            if (classId == BACKGROUND_CLASS_ID || classId > MAX_SEMANTIC_CLASS_ID) {
                throw new IllegalStateException("Semantic class ID is outside the positive uint16 range: " + classId);
            }
            if (!classIds.add(classId)) {
                throw new IllegalStateException("Duplicate semantic class ID: " + classId);
            }
            expectedClassId++;
        }
    }

    private static String sha256Hex(String canonicalData) {
        try {
            MessageDigest digest = MessageDigest.getInstance(FINGERPRINT_ALGORITHM);
            byte[] hash = digest.digest(canonicalData.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", Byte.toUnsignedInt(value)));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static final class SourceEntry {
        private final String registryIdentifier;
        private final Integer diagnosticRawId;

        public SourceEntry(String registryIdentifier, Integer diagnosticRawId) {
            this.registryIdentifier = registryIdentifier;
            this.diagnosticRawId = diagnosticRawId;
        }

        public String getRegistryIdentifier() {
            return registryIdentifier;
        }

        public Integer getDiagnosticRawId() {
            return diagnosticRawId;
        }
    }

    public static final class Entry {
        private final int semanticClassId;
        private final String registryIdentifier;
        private final Integer diagnosticRawId;

        private Entry(int semanticClassId, String registryIdentifier, Integer diagnosticRawId) {
            this.semanticClassId = semanticClassId;
            this.registryIdentifier = registryIdentifier;
            this.diagnosticRawId = diagnosticRawId;
        }

        public int getSemanticClassId() {
            return semanticClassId;
        }

        public String getRegistryIdentifier() {
            return registryIdentifier;
        }

        public Integer getDiagnosticRawId() {
            return diagnosticRawId;
        }
    }
}
