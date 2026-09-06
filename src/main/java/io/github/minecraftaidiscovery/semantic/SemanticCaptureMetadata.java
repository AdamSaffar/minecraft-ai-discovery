package io.github.minecraftaidiscovery.semantic;

import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassVocabulary;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class SemanticCaptureMetadata {
    public static final int FORMAT_VERSION = 2;
    public static final String FORMAT_NAME = "semantic-observation-v2";

    private SemanticCaptureMetadata() {
    }

    public static String toJson(
        int width,
        int height,
        String classBufferFileName,
        int classBufferLength,
        String visualizationFileName,
        BlockClassVocabulary vocabulary,
        Map<Integer, Long> pixelCounts
    ) {
        if (vocabulary == null) {
            throw new IllegalArgumentException("Block class vocabulary is missing");
        }
        if (pixelCounts == null) {
            throw new IllegalArgumentException("Semantic pixel counts are missing");
        }
        requireFileName("class buffer", classBufferFileName);
        requireFileName("visualization", visualizationFileName);
        int expectedLength = SemanticClassEncoding.expectedBufferLength(width, height);
        if (classBufferLength != expectedLength) {
            throw new IllegalArgumentException(
                "Expected class-buffer length " + expectedLength + " but found " + classBufferLength
            );
        }

        TreeMap<Integer, Long> observed = new TreeMap<>();
        long totalPixels = 0;
        for (Map.Entry<Integer, Long> count : pixelCounts.entrySet()) {
            int classId = SemanticClassEncoding.requireUnsigned16(count.getKey());
            Long pixelCount = count.getValue();
            if (pixelCount == null || pixelCount < 0) {
                throw new IllegalArgumentException("Invalid pixel count for semantic class ID " + classId);
            }
            if (pixelCount == 0) {
                continue;
            }
            if (classId != BlockClassManifest.BACKGROUND_CLASS_ID) {
                vocabulary.requireRegistryIdentifier(classId);
            }
            observed.put(classId, pixelCount);
            totalPixels = Math.addExact(totalPixels, pixelCount);
        }
        long expectedPixels = (long)width * height;
        if (totalPixels != expectedPixels) {
            throw new IllegalArgumentException(
                "Expected pixel counts to total " + expectedPixels + " but found " + totalPixels
            );
        }

        BlockClassManifest manifest = vocabulary.getManifest();
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        appendString(json, "format", FORMAT_NAME, true, 2);
        json.append("  \"formatVersion\": ").append(FORMAT_VERSION).append(",\n");
        appendString(json, "minecraftVersion", manifest.getMinecraftVersion(), true, 2);
        appendString(json, "yarnMappings", manifest.getYarnMappings(), true, 2);
        json.append("  \"width\": ").append(width).append(",\n");
        json.append("  \"height\": ").append(height).append(",\n");
        appendString(json, "pixelOrder", SemanticClassEncoding.PIXEL_ORDER, true, 2);
        json.append("  \"backgroundClassId\": ").append(BlockClassManifest.BACKGROUND_CLASS_ID).append(",\n");
        json.append("  \"manifest\": {\"entryCount\": ").append(vocabulary.getEntryCount())
            .append(", \"sha256\": \"").append(vocabulary.getFingerprintSha256()).append("\"},\n");
        json.append("  \"classBuffer\": {\"file\": \"").append(escape(classBufferFileName))
            .append("\", \"encoding\": \"").append(SemanticClassEncoding.CLASS_BUFFER_ENCODING)
            .append("\", \"byteOrder\": \"").append(SemanticClassEncoding.BYTE_ORDER)
            .append("\", \"bytesPerPixel\": ").append(SemanticClassEncoding.BYTES_PER_PIXEL)
            .append(", \"byteLength\": ").append(classBufferLength).append("},\n");
        json.append("  \"visualization\": {\"file\": \"").append(escape(visualizationFileName))
            .append("\", \"background\": \"#000000\", \"colorAssignment\": \"deterministic class-id hash\"},\n");
        json.append("  \"backgroundPixelCount\": ")
            .append(observed.getOrDefault(BlockClassManifest.BACKGROUND_CLASS_ID, 0L)).append(",\n");
        json.append("  \"observedClasses\": [\n");

        int written = 0;
        int positiveObserved = observed.containsKey(BlockClassManifest.BACKGROUND_CLASS_ID)
            ? observed.size() - 1
            : observed.size();
        for (Map.Entry<Integer, Long> count : observed.entrySet()) {
            int classId = count.getKey();
            if (classId == BlockClassManifest.BACKGROUND_CLASS_ID) {
                continue;
            }
            json.append("    {\"classId\": ").append(classId)
                .append(", \"registryIdentifier\": \"")
                .append(escape(vocabulary.requireRegistryIdentifier(classId)))
                .append("\", \"pixelCount\": ").append(count.getValue())
                .append(", \"falseColor\": \"")
                .append(String.format(Locale.ROOT, "#%06X", SemanticClassEncoding.falseColorRgb(classId)))
                .append("\"}");
            written++;
            json.append(written == positiveObserved ? "\n" : ",\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static void requireFileName(String name, String value) {
        if (value == null || value.isEmpty() || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid " + name + " file name");
        }
    }

    private static void appendString(
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
