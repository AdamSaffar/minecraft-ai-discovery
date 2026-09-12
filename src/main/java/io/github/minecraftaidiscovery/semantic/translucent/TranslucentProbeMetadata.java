package io.github.minecraftaidiscovery.semantic.translucent;

import io.github.minecraftaidiscovery.semantic.SemanticCaptureMetadata;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassVocabulary;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class TranslucentProbeMetadata {
    public static final int FORMAT_VERSION = 2;
    public static final String FORMAT_NAME = "translucent-observation-probe-v2";

    private TranslucentProbeMetadata() {
    }

    public static String toJson(
        int width,
        int height,
        String graphicsMode,
        String cameraMedium,
        Collection<String> resourcePacks,
        FogRetentionCaptureContext fogContext,
        BlockClassVocabulary vocabulary,
        Map<String, Artifact> artifacts,
        String baseVisualization,
        String fogRetentionVisualization,
        String nearestVisualization,
        long overflowPixelCount,
        long saturatedFragmentCountPixels,
        Map<Integer, Long> observedClassCounts
    ) {
        int pixels = TranslucentProbeBufferEncoding.requireDimensions(width, height);
        if (width != 640 || height != 360) {
            throw new IllegalArgumentException("Translucent probe resolution must be 640 x 360");
        }
        requireGraphicsMode(graphicsMode);
        requireText("camera medium", cameraMedium);
        if (resourcePacks == null || resourcePacks.isEmpty()) {
            throw new IllegalArgumentException("Active resource packs are missing");
        }
        if (fogContext == null || vocabulary == null || artifacts == null || artifacts.isEmpty()) {
            throw new IllegalArgumentException("Translucent probe metadata input is missing");
        }
        requireFileName(baseVisualization);
        requireFileName(fogRetentionVisualization);
        requireFileName(nearestVisualization);
        requirePixelCount("overflow", overflowPixelCount, pixels);
        requirePixelCount("fragment-count saturation", saturatedFragmentCountPixels, pixels);

        LinkedHashMap<String, Artifact> checkedArtifacts = new LinkedHashMap<>();
        for (Map.Entry<String, Artifact> entry : artifacts.entrySet()) {
            requireText("artifact name", entry.getKey());
            Artifact artifact = entry.getValue();
            if (artifact == null) {
                throw new IllegalArgumentException("Translucent probe artifact is missing");
            }
            artifact.validate(width, height);
            checkedArtifacts.put(entry.getKey(), artifact);
        }

        TreeMap<Integer, Long> observed = new TreeMap<>();
        if (observedClassCounts == null) {
            throw new IllegalArgumentException("Observed translucent classes are missing");
        }
        for (Map.Entry<Integer, Long> entry : observedClassCounts.entrySet()) {
            int classId = entry.getKey();
            Long count = entry.getValue();
            if (classId <= BlockClassManifest.BACKGROUND_CLASS_ID
                || count == null
                || count <= 0) {
                throw new IllegalArgumentException("Observed translucent class entry is invalid");
            }
            vocabulary.requireRegistryIdentifier(classId);
            observed.put(classId, count);
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        appendString(json, "format", FORMAT_NAME, true, 2);
        json.append("  \"formatVersion\": ").append(FORMAT_VERSION).append(",\n");
        json.append("  \"status\": {\"experimental\": true, \"researcherOnly\": true, ")
            .append("\"agentVisible\": false, \"productionObservationContract\": false},\n");
        json.append("  \"ordinarySemanticCapture\": {\"format\": \"")
            .append(SemanticCaptureMetadata.FORMAT_NAME)
            .append("\", \"unchanged\": true},\n");
        BlockClassManifest manifest = vocabulary.getManifest();
        appendString(json, "minecraftVersion", manifest.getMinecraftVersion(), true, 2);
        appendString(json, "yarnMappings", manifest.getYarnMappings(), true, 2);
        json.append("  \"width\": ").append(width).append(",\n");
        json.append("  \"height\": ").append(height).append(",\n");
        appendString(json, "pixelOrder", TranslucentProbeBufferEncoding.PIXEL_ORDER, true, 2);
        appendString(json, "graphicsMode", graphicsMode, true, 2);
        appendString(json, "cameraMedium", cameraMedium, true, 2);
        appendStringArray(json, "resourcePacks", resourcePacks);
        json.append("  \"manifest\": {\"entryCount\": ").append(vocabulary.getEntryCount())
            .append(", \"sha256\": \"").append(vocabulary.getFingerprintSha256()).append("\"},\n");
        appendFogRetention(json, fogContext);
        json.append("  \"renderEvidence\": {\n");
        json.append("    \"baseLayers\": [\"solid\", \"cutout_mipped\", \"cutout\"],\n");
        json.append("    \"translucentLayer\": \"translucent\",\n");
        json.append("    \"quadOrder\": \"vanilla BufferBuilder sort permutation mirrored on build and resort\",\n");
        json.append("    \"blockModelSource\": \"block_model\",\n");
        json.append("    \"fluidSource\": \"fluid_renderer\",\n");
        json.append("    \"fluidIdentities\": {\"minecraft:water\": ")
            .append(vocabulary.requireClassId(TranslucentProbeFluidIdentity.WATER))
            .append(", \"minecraft:lava\": ")
            .append(vocabulary.requireClassId(TranslucentProbeFluidIdentity.LAVA)).append("}\n");
        json.append("  },\n");
        json.append("  \"temporaryStorage\": {\"capacity\": ")
            .append(TranslucentProbeCandidates.TEMPORARY_STORAGE_CAPACITY)
            .append(", \"productionK\": false, \"overflowPixelCount\": ")
            .append(overflowPixelCount).append("},\n");
        json.append("  \"measurements\": {\n");
        json.append("    \"depth\": \"float32 window depth [0,1], researcher-only\",\n");
        json.append("    \"sampledAlpha\": \"uint8 block-atlas sample, researcher-only\",\n");
        json.append("    \"accumulatedOpacity\": \"uint8 source-over alpha, researcher-only\",\n");
        json.append("    \"acceptedFragmentCount\": \"uint8 saturated at 255, researcher-only\",\n");
        json.append("    \"baseFogRetention\": \"uint8 fixed-function factor, researcher-only\",\n");
        json.append("    \"saturatedFragmentCountPixels\": ").append(saturatedFragmentCountPixels)
            .append(",\n");
        json.append("    \"zeroAlphaPolicy\": \"discarded and explicitly excluded from vanilla zero-alpha depth-write equivalence\"\n");
        json.append("  },\n");
        json.append("  \"candidateComparisons\": {\n");
        appendString(json, "frontToBack", TranslucentProbeCandidates.FRONT_TO_BACK_ENCODING, true, 4);
        appendString(
            json,
            "contributionRanked",
            TranslucentProbeCandidates.CONTRIBUTION_RANKED_ENCODING,
            false,
            4
        );
        json.append("  },\n");
        json.append("  \"artifacts\": {\n");
        int artifactIndex = 0;
        for (Map.Entry<String, Artifact> entry : checkedArtifacts.entrySet()) {
            Artifact artifact = entry.getValue();
            json.append("    \"").append(escape(entry.getKey())).append("\": {\"file\": \"")
                .append(escape(artifact.fileName)).append("\", \"encoding\": \"")
                .append(escape(artifact.encoding)).append("\", \"bytesPerPixel\": ")
                .append(artifact.bytesPerPixel).append(", \"byteLength\": ")
                .append(artifact.byteLength).append("}");
            artifactIndex++;
            json.append(artifactIndex == checkedArtifacts.size() ? "\n" : ",\n");
        }
        json.append("  },\n");
        json.append("  \"visualizations\": {\"base\": \"")
            .append(escape(baseVisualization)).append("\", \"baseFogRetention\": \"")
            .append(escape(fogRetentionVisualization))
            .append("\", \"frontmostTranslucent\": \"")
            .append(escape(nearestVisualization)).append("\", \"colors\": ")
            .append("\"deterministic class-id hash; diagnostic only\"},\n");
        json.append("  \"observedContributionClasses\": [\n");
        int observedIndex = 0;
        for (Map.Entry<Integer, Long> entry : observed.entrySet()) {
            json.append("    {\"classId\": ").append(entry.getKey())
                .append(", \"registryIdentifier\": \"")
                .append(escape(vocabulary.requireRegistryIdentifier(entry.getKey())))
                .append("\", \"storedPixelOccurrences\": ").append(entry.getValue()).append("}");
            observedIndex++;
            json.append(observedIndex == observed.size() ? "\n" : ",\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static void appendFogRetention(
        StringBuilder json,
        FogRetentionCaptureContext context
    ) {
        FogRetentionParameters parameters = context.getParameters();
        json.append("  \"baseFogRetention\": {\n");
        json.append("    \"coverage\": \"winning solid, cutout_mipped, or cutout terrain fragment\",\n");
        json.append("    \"method\": \"Minecraft fixed-function fog over white terrain and black fog color\",\n");
        json.append("    \"quantization\": \"normalized uint8 framebuffer readback\",\n");
        json.append("    \"supportedGraphicsModes\": [\"fast\", \"fancy\"],\n");
        json.append("    \"resourcePackPolicy\": \"vanilla only\",\n");
        json.append("    \"postShaderPolicy\": \"none loaded\",\n");
        appendString(json, "fogType", "terrain", true, 4);
        appendString(json, "mode", parameters.getMode().getMetadataName(), true, 4);
        json.append("    \"density\": ").append(parameters.getDensity()).append(",\n");
        json.append("    \"start\": ").append(parameters.getStart()).append(",\n");
        json.append("    \"end\": ").append(parameters.getEnd()).append(",\n");
        appendString(
            json,
            "distanceMode",
            parameters.getDistanceMode().getMetadataName(),
            true,
            4
        );
        json.append("    \"nvFogDistanceSupported\": ")
            .append(parameters.isNvFogDistanceSupported()).append(",\n");
        json.append("    \"underwaterVisibility\": ")
            .append(context.getUnderwaterVisibility()).append(",\n");
        json.append("    \"waterBiomeDensityModifier\": ")
            .append(context.getBiomeDensityModifier()).append(",\n");
        json.append("    \"viewDistanceChunks\": ")
            .append(context.getViewDistanceChunks()).append(",\n");
        json.append("    \"terrainFogViewDistance\": ")
            .append(context.getTerrainFogViewDistance()).append(",\n");
        appendString(json, "perspective", context.getPerspective(), true, 4);
        json.append("    \"thickFog\": ").append(context.isThickFog()).append(",\n");
        json.append("    \"skyFogAppliedEarlier\": ")
            .append(context.isSkyFogApplied()).append(",\n");
        json.append("    \"statusEffects\": {\"blindness\": ")
            .append(context.hasBlindness()).append(", \"blindnessDurationTicks\": ")
            .append(context.getBlindnessDurationTicks()).append(", \"fireResistance\": ")
            .append(context.hasFireResistance()).append(", \"nausea\": ")
            .append(context.hasNausea()).append(", \"nightVision\": ")
            .append(context.hasNightVision()).append("},\n");
        json.append("    \"projectionMatrixColumnMajor\": [");
        float[] projection = context.copyProjectionMatrix();
        for (int index = 0; index < projection.length; index++) {
            if (index > 0) {
                json.append(", ");
            }
            json.append(projection[index]);
        }
        json.append("]\n");
        json.append("  },\n");
    }

    private static void appendStringArray(
        StringBuilder json,
        String name,
        Collection<String> values
    ) {
        json.append("  \"").append(name).append("\": [");
        int index = 0;
        for (String value : values) {
            requireText(name, value);
            if (index > 0) {
                json.append(", ");
            }
            json.append('"').append(escape(value)).append('"');
            index++;
        }
        json.append("],\n");
    }

    private static void appendString(
        StringBuilder json,
        String name,
        String value,
        boolean trailingComma,
        int spaces
    ) {
        requireText(name, value);
        for (int index = 0; index < spaces; index++) {
            json.append(' ');
        }
        json.append('"').append(name).append("\": \"").append(escape(value)).append('"');
        json.append(trailingComma ? ",\n" : "\n");
    }

    private static void requireGraphicsMode(String graphicsMode) {
        if (!"fast".equals(graphicsMode)
            && !"fancy".equals(graphicsMode)
            && !"fabulous".equals(graphicsMode)) {
            throw new IllegalArgumentException("Unsupported graphics mode: " + graphicsMode);
        }
    }

    private static void requirePixelCount(String name, long value, int pixels) {
        if (value < 0 || value > pixels) {
            throw new IllegalArgumentException("Invalid " + name + " pixel count");
        }
    }

    private static void requireFileName(String value) {
        requireText("file name", value);
        if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Artifact name must not contain a path");
        }
    }

    private static void requireText(String name, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing " + name);
        }
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

    public static final class Artifact {
        private final String fileName;
        private final String encoding;
        private final int bytesPerPixel;
        private final int byteLength;

        public Artifact(String fileName, String encoding, int bytesPerPixel, int byteLength) {
            requireFileName(fileName);
            requireText("artifact encoding", encoding);
            if (bytesPerPixel <= 0 || byteLength <= 0) {
                throw new IllegalArgumentException("Artifact size must be positive");
            }
            this.fileName = fileName;
            this.encoding = encoding;
            this.bytesPerPixel = bytesPerPixel;
            this.byteLength = byteLength;
        }

        private void validate(int width, int height) {
            int expected = Math.multiplyExact(
                TranslucentProbeBufferEncoding.requireDimensions(width, height),
                bytesPerPixel
            );
            if (byteLength != expected) {
                throw new IllegalArgumentException(
                    "Artifact " + fileName + " has length " + byteLength + ", expected " + expected
                );
            }
        }
    }
}
