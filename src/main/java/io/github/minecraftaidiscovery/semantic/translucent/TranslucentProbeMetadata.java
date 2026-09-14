package io.github.minecraftaidiscovery.semantic.translucent;

import io.github.minecraftaidiscovery.semantic.SemanticCaptureMetadata;
import io.github.minecraftaidiscovery.semantic.firstperson.FirstPersonDepthCoverageEncoding;
import io.github.minecraftaidiscovery.semantic.firstperson.FirstPersonDepthCoverageResult;
import io.github.minecraftaidiscovery.semantic.lighting.LightingRetentionCaptureContext;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassVocabulary;
import io.github.minecraftaidiscovery.semantic.overlay.CameraOverlayKind;
import io.github.minecraftaidiscovery.semantic.overlay.CameraOverlayRetentionResult;
import io.github.minecraftaidiscovery.semantic.visibility.SameFrameRgbCaptureBoundary;
import io.github.minecraftaidiscovery.semantic.visibility.SameFrameRgbEncoding;
import io.github.minecraftaidiscovery.semantic.visibility.SameFrameRgbResult;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class TranslucentProbeMetadata {
    public static final int FORMAT_VERSION = 6;
    public static final String FORMAT_NAME = "translucent-observation-probe-v6";

    private TranslucentProbeMetadata() {
    }

    public static String toJson(
        int width,
        int height,
        String graphicsMode,
        String cameraMedium,
        Collection<String> resourcePacks,
        FogRetentionCaptureContext fogContext,
        LightingRetentionCaptureContext lightingContext,
        BlockClassVocabulary vocabulary,
        Map<String, Artifact> artifacts,
        String baseVisualization,
        String lightingRetentionVisualization,
        String fogRetentionVisualization,
        String cameraOverlayRetentionVisualization,
        String firstPersonDepthCoverageVisualization,
        String nearestVisualization,
        String sameFrameRgbVisualization,
        long validLightingBasePixelCount,
        long unsupportedLightingBasePixelCount,
        long positiveBasePixelCount,
        long fullScreenFirstPersonCoveredPixelCount,
        long coveredBasePixelCount,
        long overflowPixelCount,
        long saturatedFragmentCountPixels,
        CameraOverlayRetentionResult cameraOverlayRetention,
        FirstPersonDepthCoverageResult firstPersonDepthCoverage,
        SameFrameRgbResult sameFrameRgb,
        SameFrameRgbCaptureBoundary sameFrameRgbBoundary,
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
        if (fogContext == null
            || lightingContext == null
            || cameraOverlayRetention == null
            || firstPersonDepthCoverage == null
            || sameFrameRgb == null
            || sameFrameRgbBoundary == null
            || vocabulary == null
            || artifacts == null
            || artifacts.isEmpty()) {
            throw new IllegalArgumentException("Translucent probe metadata input is missing");
        }
        requireFileName(baseVisualization);
        requireFileName(lightingRetentionVisualization);
        requireFileName(fogRetentionVisualization);
        requireFileName(cameraOverlayRetentionVisualization);
        requireFileName(firstPersonDepthCoverageVisualization);
        requireFileName(nearestVisualization);
        requireFileName(sameFrameRgbVisualization);
        requirePixelCount("valid lighting base", validLightingBasePixelCount, pixels);
        requirePixelCount("unsupported lighting base", unsupportedLightingBasePixelCount, pixels);
        if (validLightingBasePixelCount + unsupportedLightingBasePixelCount > pixels) {
            throw new IllegalArgumentException("Lighting base pixel counts exceed the image");
        }
        requirePixelCount(
            "valid camera-overlay base",
            positiveBasePixelCount,
            pixels
        );
        if (cameraOverlayRetention.getWidth() != width
            || cameraOverlayRetention.getHeight() != height) {
            throw new IllegalArgumentException(
                "Camera-overlay retention dimensions do not match the metadata"
            );
        }
        if (firstPersonDepthCoverage.getWidth() != width
            || firstPersonDepthCoverage.getHeight() != height) {
            throw new IllegalArgumentException(
                "First-person depth-coverage dimensions do not match the metadata"
            );
        }
        if (sameFrameRgb.getSourceWidth() != SameFrameRgbEncoding.SOURCE_WIDTH
            || sameFrameRgb.getSourceHeight() != SameFrameRgbEncoding.SOURCE_HEIGHT
            || sameFrameRgb.getWidth() != width
            || sameFrameRgb.getHeight() != height) {
            throw new IllegalArgumentException(
                "Same-frame RGB dimensions do not match the metadata"
            );
        }
        if (sameFrameRgbBoundary
                == SameFrameRgbCaptureBoundary.RENDER_WORLD_RETURN_WITHOUT_HAND
            && (cameraOverlayRetention.wasOverlayStageEntered()
                || firstPersonDepthCoverage.wasPostHandDepthSampled())) {
            throw new IllegalArgumentException(
                "Fallback same-frame RGB boundary conflicts with completed hand-stage data"
            );
        }
        if (firstPersonDepthCoverage.wasPostHandDepthSampled()
            != cameraOverlayRetention.wasOverlayStageEntered()) {
            throw new IllegalArgumentException(
                "First-person depth sampling does not match the overlay-stage lifecycle"
            );
        }
        requirePixelCount(
            "full-screen first-person coverage",
            fullScreenFirstPersonCoveredPixelCount,
            pixels
        );
        requirePixelCount("covered first-person base", coveredBasePixelCount, pixels);
        if (fullScreenFirstPersonCoveredPixelCount
            != FirstPersonDepthCoverageEncoding.countCovered(
                firstPersonDepthCoverage.copyFullScreenCoverage()
            )) {
            throw new IllegalArgumentException(
                "First-person full-screen coverage count does not match its buffer"
            );
        }
        if (coveredBasePixelCount > fullScreenFirstPersonCoveredPixelCount
            || coveredBasePixelCount > positiveBasePixelCount) {
            throw new IllegalArgumentException(
                "First-person covered base count exceeds its source coverage"
            );
        }
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
        appendSameFrameRgb(
            json,
            sameFrameRgb,
            sameFrameRgbBoundary,
            cameraOverlayRetention.wasOverlayStageEntered(),
            coveredBasePixelCount > 0
        );
        appendLightingRetention(
            json,
            lightingContext,
            validLightingBasePixelCount,
            unsupportedLightingBasePixelCount
        );
        appendCameraOverlayRetention(
            json,
            fogContext.getPerspective(),
            cameraOverlayRetention,
            positiveBasePixelCount,
            pixels
        );
        appendFirstPersonDepthCoverage(
            json,
            fogContext.getPerspective(),
            firstPersonDepthCoverage,
            fullScreenFirstPersonCoveredPixelCount,
            coveredBasePixelCount,
            positiveBasePixelCount,
            pixels
        );
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
        json.append("    \"baseLightingRetention\": \"uint8 RGB renderer-lighting response, researcher-only\",\n");
        json.append("    \"baseFogRetention\": \"uint8 fixed-function factor, researcher-only\",\n");
        json.append("    \"baseCameraOverlayRetention\": \"uint8 retained pre-overlay RGB fraction, researcher-only\",\n");
        json.append("    \"baseFirstPersonDepthCoverage\": \"uint8 binary post-hand depth write, researcher-only\",\n");
        json.append("    \"sameFrameRgb\": \"uint8 RGB pre-HUD main-framebuffer sample, researcher-only\",\n");
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
            .append(escape(baseVisualization)).append("\", \"baseLightingRetention\": \"")
            .append(escape(lightingRetentionVisualization))
            .append("\", \"baseFogRetention\": \"")
            .append(escape(fogRetentionVisualization))
            .append("\", \"baseCameraOverlayRetention\": \"")
            .append(escape(cameraOverlayRetentionVisualization))
            .append("\", \"baseFirstPersonDepthCoverage\": \"")
            .append(escape(firstPersonDepthCoverageVisualization))
            .append("\", \"frontmostTranslucent\": \"")
            .append(escape(nearestVisualization)).append("\", \"sameFrameRgb\": \"")
            .append(escape(sameFrameRgbVisualization)).append("\", \"colors\": ")
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

    private static void appendSameFrameRgb(
        StringBuilder json,
        SameFrameRgbResult result,
        SameFrameRgbCaptureBoundary boundary,
        boolean overlayStageEntered,
        boolean firstPersonDepthCoverageOnPositiveBasePixels
    ) {
        json.append("  \"sameFrameRgb\": {\n");
        appendString(json, "status", "valid", true, 4);
        json.append("    \"coverage\": \"ordinary main framebuffer after world and any rendered first-person/camera overlays, before post-processing, HUD, menus, and cursor\",\n");
        json.append("    \"method\": \"one direct RGBA8 read of the already-rendered main framebuffer followed by non-overlapping 3x3 byte-space box averaging\",\n");
        appendString(json, "captureBoundary", boundary.getMetadataName(), true, 4);
        json.append("    \"handAndCameraOverlayStageComplete\": ")
            .append(boundary.isHandAndCameraOverlayStageComplete()).append(",\n");
        json.append("    \"overlayStageEntered\": ").append(overlayStageEntered)
            .append(",\n");
        json.append("    \"firstPersonDepthCoverageOnPositiveBasePixels\": ")
            .append(firstPersonDepthCoverageOnPositiveBasePixels).append(",\n");
        json.append("    \"sourceWidth\": ").append(result.getSourceWidth()).append(",\n");
        json.append("    \"sourceHeight\": ").append(result.getSourceHeight()).append(",\n");
        json.append("    \"width\": ").append(result.getWidth()).append(",\n");
        json.append("    \"height\": ").append(result.getHeight()).append(",\n");
        json.append("    \"byteLength\": ")
            .append(SameFrameRgbEncoding.expectedRgbLength()).append(",\n");
        json.append("    \"sourceEncoding\": \"RGBA8 stored framebuffer bytes, bottom-to-top rows\",\n");
        appendString(json, "encoding", SameFrameRgbEncoding.ENCODING, true, 4);
        json.append("    \"pixelOrder\": \"row-major, top-left origin\",\n");
        json.append("    \"channelOrder\": \"RGB\",\n");
        json.append("    \"downsampleFactor\": ")
            .append(SameFrameRgbEncoding.DOWNSAMPLE_FACTOR).append(",\n");
        json.append("    \"averagingColorSpace\": \"stored byte values; no linear-light reconstruction\",\n");
        json.append("    \"sameFrameAsProbeBuffers\": true,\n");
        json.append("    \"preHud\": true,\n");
        json.append("    \"preMenu\": true,\n");
        json.append("    \"prePostProcess\": true,\n");
        json.append("    \"additionalRenderPass\": false,\n");
        json.append("    \"additionalPlayerOrWorldQueries\": false,\n");
        json.append("    \"rawNativeResolutionSerialized\": false,\n");
        json.append("    \"thresholdApplied\": false,\n");
        json.append("    \"agentVisible\": false,\n");
        json.append("    \"perceptualVisibilityScore\": false,\n");
        json.append("    \"resourcePackPolicy\": \"vanilla only\",\n");
        json.append("    \"postShaderPolicy\": \"none loaded\",\n");
        json.append("    \"unsupportedStates\": [\"framebuffer other than exactly 1920x1080 RGBA8\", \"active post shader\", \"fabulous graphics\", \"non-vanilla resource packs\", \"multisample readback\", \"pixel-pack buffer readback\", \"main framebuffer or viewport not active\"]\n");
        json.append("  },\n");
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

    private static void appendLightingRetention(
        StringBuilder json,
        LightingRetentionCaptureContext context,
        long validBasePixelCount,
        long unsupportedBasePixelCount
    ) {
        json.append("  \"baseLightingRetention\": {\n");
        appendString(
            json,
            "status",
            unsupportedBasePixelCount == 0 ? "valid" : "partial",
            true,
            4
        );
        json.append("    \"coverage\": \"winning positive block-model fragment in solid, cutout_mipped, or cutout\",\n");
        json.append("    \"method\": \"Minecraft active lightmap sampled at renderer-emitted coordinates and multiplied by the renderer AO/directional value\",\n");
        json.append("    \"excludedColorTerms\": [\"block atlas RGB\", \"baked quad color\", \"biome tint\"],\n");
        json.append("    \"separateFromFog\": true,\n");
        json.append("    \"thresholdApplied\": false,\n");
        json.append("    \"agentVisible\": false,\n");
        json.append("    \"quantization\": \"renderer multiplier uint8 sidecar and normalized RGB8 framebuffer readback\",\n");
        json.append("    \"validity\": \"255 for a supported positive block-model base fragment; 0 means background or unsupported source, not a measured dark value\",\n");
        json.append("    \"validBasePixelCount\": ").append(validBasePixelCount).append(",\n");
        json.append("    \"unsupportedBasePixelCount\": ")
            .append(unsupportedBasePixelCount).append(",\n");
        json.append("    \"unsupportedPixelSources\": [\"fluid renderer\", \"base geometry not emitted through BlockModelRenderer.renderQuad\"],\n");
        json.append("    \"unsupportedStatePolicy\": \"capture fails instead of substituting a distance or world-light approximation\",\n");
        json.append("    \"unsupportedStates\": [\"fabulous graphics\", \"non-vanilla resource packs\", \"active post shader\", \"missing or mismatched renderer lighting sidecar\", \"inactive or nonstandard lightmap texture state\"],\n");
        json.append("    \"tickDelta\": ").append(context.getTickDelta()).append(",\n");
        json.append("    \"gamma\": ").append(context.getGamma()).append(",\n");
        json.append("    \"ordinaryGammaRange\": ")
            .append(context.isOrdinaryGammaRange()).append(",\n");
        json.append("    \"ambientOcclusionEnabled\": ")
            .append(context.isAmbientOcclusionEnabled()).append(",\n");
        appendString(json, "world", context.getWorld(), true, 4);
        appendString(json, "dimensionType", context.getDimensionType(), true, 4);
        json.append("    \"dimensionLighting\": {\"hasSkyLight\": ")
            .append(context.hasSkyLight()).append(", \"hasFixedTime\": ")
            .append(context.hasFixedTime()).append(", \"darkenedDirectionalLighting\": ")
            .append(context.hasDarkenedDirectionalLighting())
            .append(", \"endLightmapTransform\": ")
            .append(context.hasEndLightmapTransform()).append("},\n");
        json.append("    \"worldTime\": ").append(context.getWorldTime()).append(",\n");
        json.append("    \"timeOfDay\": ").append(context.getTimeOfDay()).append(",\n");
        json.append("    \"rainGradient\": ").append(context.getRainGradient()).append(",\n");
        json.append("    \"thunderGradient\": ").append(context.getThunderGradient()).append(",\n");
        json.append("    \"lightningTicksLeft\": ")
            .append(context.getLightningTicksLeft()).append(",\n");
        json.append("    \"bossSkyDarkness\": ")
            .append(context.getSkyDarkness()).append(",\n");
        json.append("    \"underwaterVisibility\": ")
            .append(context.getUnderwaterVisibility()).append(",\n");
        json.append("    \"statusEffects\": {\"nightVision\": ")
            .append(context.hasNightVision()).append(", \"nightVisionDurationTicks\": ")
            .append(context.getNightVisionDurationTicks()).append(", \"nightVisionStrength\": ")
            .append(context.getNightVisionStrength()).append(", \"conduitPower\": ")
            .append(context.hasConduitPower()).append(", \"conduitPowerDurationTicks\": ")
            .append(context.getConduitPowerDurationTicks()).append("}\n");
        json.append("  },\n");
    }

    private static void appendCameraOverlayRetention(
        StringBuilder json,
        String perspective,
        CameraOverlayRetentionResult result,
        long validBasePixelCount,
        int pixels
    ) {
        json.append("  \"baseCameraOverlayRetention\": {\n");
        appendString(json, "status", "valid", true, 4);
        json.append("    \"coverage\": \"winning positive solid, cutout_mipped, or cutout base pixels\",\n");
        json.append("    \"method\": \"original renderer-emitted camera-overlay quads mirrored over white with source RGB removed\",\n");
        json.append("    \"supportedOverlayPaths\": [\"inside_block\", \"underwater\", \"fire\"],\n");
        json.append("    \"overlayStageEntered\": ")
            .append(result.wasOverlayStageEntered()).append(",\n");
        appendString(json, "perspective", perspective, true, 4);
        json.append("    \"activeOverlayDraws\": [");
        int activeIndex = 0;
        for (Map.Entry<CameraOverlayKind, Integer> entry : result.getDrawCounts().entrySet()) {
            if (activeIndex > 0) {
                json.append(", ");
            }
            json.append("{\"kind\": \"").append(entry.getKey().getMetadataName())
                .append("\", \"drawCount\": ").append(entry.getValue()).append("}");
            activeIndex++;
        }
        json.append("],\n");
        json.append("    \"validity\": \"valid only where base.classes.u16le is positive; stored zero on background\",\n");
        json.append("    \"validBasePixelCount\": ").append(validBasePixelCount).append(",\n");
        json.append("    \"backgroundPixelCount\": ")
            .append(pixels - validBasePixelCount).append(",\n");
        json.append("    \"thresholdApplied\": false,\n");
        json.append("    \"agentVisible\": false,\n");
        json.append("    \"separateFromFog\": true,\n");
        json.append("    \"perceptualVisibilityScore\": false,\n");
        json.append("    \"serializedRendererInputs\": false,\n");
        json.append("    \"additionalWorldQueries\": false,\n");
        json.append("    \"resourcePackPolicy\": \"vanilla only\",\n");
        json.append("    \"postShaderPolicy\": \"none loaded\",\n");
        json.append("    \"unsupportedStates\": [\"fabulous graphics\", \"non-vanilla resource packs\", \"active post shader\", \"unexpected overlay draw format or count\", \"unexpected fixed-function render state\"]\n");
        json.append("  },\n");
    }

    private static void appendFirstPersonDepthCoverage(
        StringBuilder json,
        String perspective,
        FirstPersonDepthCoverageResult result,
        long fullScreenCoveredPixelCount,
        long coveredBasePixelCount,
        long positiveBasePixelCount,
        int pixels
    ) {
        json.append("  \"baseFirstPersonDepthCoverage\": {\n");
        appendString(json, "status", "valid", true, 4);
        json.append("    \"coverage\": \"winning positive solid, cutout_mipped, or cutout base pixels\",\n");
        json.append("    \"method\": \"nearest-scaled main-framebuffer depth sampled after the first-person hand/item flush and before camera overlays\",\n");
        json.append("    \"postHandDepthSampled\": ")
            .append(result.wasPostHandDepthSampled()).append(",\n");
        appendString(json, "perspective", perspective, true, 4);
        json.append("    \"clearDepth\": ")
            .append(FirstPersonDepthCoverageEncoding.CLEAR_DEPTH).append(",\n");
        json.append("    \"values\": {\"clear\": 0, \"depthWritten\": 255},\n");
        json.append("    \"pixelOrder\": \"top-to-bottom rows, left-to-right pixels\",\n");
        json.append("    \"fullScreenCoveredPixelCount\": ")
            .append(fullScreenCoveredPixelCount).append(",\n");
        json.append("    \"coveredBasePixelCount\": ")
            .append(coveredBasePixelCount).append(",\n");
        json.append("    \"positiveBasePixelCount\": ")
            .append(positiveBasePixelCount).append(",\n");
        json.append("    \"backgroundPixelCount\": ")
            .append(pixels - positiveBasePixelCount).append(",\n");
        json.append("    \"validity\": \"serialized only on positive base pixels; zero on background and when the first-person stage is absent\",\n");
        json.append("    \"nearestScaledFromWindowFramebuffer\": true,\n");
        json.append("    \"independent640RasterizationClaim\": false,\n");
        json.append("    \"binaryClearDepthTestOnly\": true,\n");
        json.append("    \"visibilityThresholdApplied\": false,\n");
        json.append("    \"agentVisible\": false,\n");
        json.append("    \"perceptualVisibilityScore\": false,\n");
        json.append("    \"opacityOrRetentionMeasurement\": false,\n");
        json.append("    \"rawDepthSerialized\": false,\n");
        json.append("    \"handOrItemIdentitySerialized\": false,\n");
        json.append("    \"rendererReplay\": false,\n");
        json.append("    \"additionalPlayerOrWorldQueries\": false,\n");
        json.append("    \"knownLimitations\": [\"translucent fragments can write depth without fully replacing RGB\", \"color-only glint and decorative layers are not independently represented\"],\n");
        json.append("    \"resourcePackPolicy\": \"vanilla only\",\n");
        json.append("    \"postShaderPolicy\": \"none loaded\",\n");
        json.append("    \"unsupportedStates\": [\"fabulous graphics\", \"non-vanilla resource packs\", \"active post shader\", \"missing or mismatched depth attachment\", \"non-1.0 depth clear\", \"invalid depth readback\"]\n");
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
