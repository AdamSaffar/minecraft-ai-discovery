package io.github.minecraftaidiscovery.semantic.manifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class BlockClassManifestJson {
    private BlockClassManifestJson() {
    }

    public static Path write(BlockClassManifest manifest, Path outputPath) throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(outputPath, toJson(manifest).getBytes(StandardCharsets.UTF_8));
        return outputPath;
    }

    public static String toJson(BlockClassManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("Manifest is missing");
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"schemaVersion\": ").append(manifest.getSchemaVersion()).append(",\n");
        json.append("  \"candidate\": true,\n");
        appendStringProperty(json, "minecraftVersion", manifest.getMinecraftVersion(), true, 2);
        appendStringProperty(json, "yarnMappings", manifest.getYarnMappings(), true, 2);
        appendStringProperty(json, "registry", manifest.getRegistryName(), true, 2);
        json.append("  \"backgroundClassId\": ").append(manifest.getBackgroundClassId()).append(",\n");
        json.append("  \"entryCount\": ").append(manifest.getEntries().size()).append(",\n");
        json.append("  \"fingerprint\": {\n");
        appendStringProperty(json, "algorithm", BlockClassManifest.FINGERPRINT_ALGORITHM, true, 4);
        appendStringProperty(
            json,
            "canonicalDataFormat",
            BlockClassManifest.FINGERPRINT_CANONICAL_DATA_FORMAT,
            true,
            4
        );
        appendStringProperty(json, "sha256", manifest.getFingerprintSha256(), false, 4);
        json.append("  },\n");
        json.append("  \"entries\": [\n");

        List<BlockClassManifest.Entry> entries = manifest.getEntries();
        for (int index = 0; index < entries.size(); index++) {
            BlockClassManifest.Entry entry = entries.get(index);
            json.append("    {\"semanticClassId\": ").append(entry.getSemanticClassId());
            json.append(", \"registryIdentifier\": \"")
                .append(escape(entry.getRegistryIdentifier()))
                .append('"');
            if (entry.getDiagnosticRawId() != null) {
                json.append(", \"diagnosticRawId\": ").append(entry.getDiagnosticRawId());
            }
            json.append('}').append(index + 1 == entries.size() ? "\n" : ",\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static void appendStringProperty(
        StringBuilder json,
        String name,
        String value,
        boolean trailingComma,
        int spaces
    ) {
        for (int index = 0; index < spaces; index++) {
            json.append(' ');
        }
        json.append('"').append(name).append("\": \"").append(escape(value)).append('"');
        json.append(trailingComma ? ",\n" : "\n");
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int)character));
                    } else {
                        escaped.append(character);
                    }
            }
        }
        return escaped.toString();
    }
}
