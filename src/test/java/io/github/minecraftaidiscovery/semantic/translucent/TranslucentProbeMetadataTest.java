package io.github.minecraftaidiscovery.semantic.translucent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.minecraftaidiscovery.semantic.SemanticCaptureMetadata;
import io.github.minecraftaidiscovery.semantic.lighting.LightingRetentionCaptureContext;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifest;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassVocabulary;
import io.github.minecraftaidiscovery.semantic.overlay.CameraOverlayKind;
import io.github.minecraftaidiscovery.semantic.overlay.CameraOverlayRetentionResult;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class TranslucentProbeMetadataTest {
    @Test
    public void marksProbeArtifactsAsResearcherOnlyAndKeepsOrdinaryV3Unchanged() {
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
            FogRetentionCaptureContext.createForTesting(
                FogRetentionParameters.createForTesting(
                    FogRetentionParameters.Mode.EXP2,
                    FogRetentionParameters.DistanceMode.NV_EYE_RADIAL,
                    0.04F,
                    0.0F,
                    1.0F
                )
            ),
            LightingRetentionCaptureContext.createForTesting(),
            vocabulary,
            artifacts,
            "base.png",
            "base-lighting-retention.png",
            "base-fog-retention.png",
            "base-camera-overlay-retention.png",
            "translucent-frontmost.png",
            12,
            0,
            12,
            3,
            1,
            overlayResult(),
            observed
        );

        assertTrue(json.contains("\"format\": \"translucent-observation-probe-v4\""));
        assertTrue(json.contains("\"experimental\": true"));
        assertTrue(json.contains("\"researcherOnly\": true"));
        assertTrue(json.contains("\"agentVisible\": false"));
        assertTrue(json.contains("\"productionObservationContract\": false"));
        assertTrue(json.contains("\"format\": \"" + SemanticCaptureMetadata.FORMAT_NAME + "\""));
        assertTrue(json.contains("\"unchanged\": true"));
        assertTrue(json.contains("\"productionK\": false"));
        assertTrue(json.contains("\"minecraft:water\": 3"));
        assertTrue(json.contains("\"minecraft:lava\": 2"));
        assertTrue(json.contains("\"mode\": \"exp2\""));
        assertTrue(json.contains("\"distanceMode\": \"nv_eye_radial\""));
        assertTrue(json.contains("\"baseFogRetention\": \"base-fog-retention.png\""));
        assertTrue(json.contains("\"baseLightingRetention\": \"base-lighting-retention.png\""));
        assertTrue(json.contains(
            "\"baseCameraOverlayRetention\": \"base-camera-overlay-retention.png\""
        ));
        assertTrue(json.contains("\"kind\": \"underwater\", \"drawCount\": 1"));
        assertTrue(json.contains("\"additionalWorldQueries\": false"));
        assertTrue(json.contains("\"perceptualVisibilityScore\": false"));
        assertTrue(json.contains("\"status\": \"valid\""));
        assertTrue(json.contains("\"thresholdApplied\": false"));
        assertTrue(json.contains("\"separateFromFog\": true"));
        assertTrue(json.contains("\"ordinaryGammaRange\": true"));
        assertTrue(json.contains(vocabulary.getFingerprintSha256()));
        assertFalse(json.contains("diagnosticRawId"));
        assertFalse(json.contains("selectedBlock"));
        assertFalse(json.contains("textureIdentity"));
        assertFalse(json.contains("rawUv"));
        assertFalse(json.contains("playerFlags"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsProductionSizedOutputInTheProbeSchema() {
        TranslucentProbeMetadata.toJson(
            1920,
            1080,
            "fast",
            "none",
            Arrays.asList("vanilla"),
            FogRetentionCaptureContext.createForTesting(
                FogRetentionParameters.createForTesting(
                    FogRetentionParameters.Mode.LINEAR,
                    FogRetentionParameters.DistanceMode.STANDARD_EYE_PLANE_ABSOLUTE,
                    0.0F,
                    24.0F,
                    32.0F
                )
            ),
            LightingRetentionCaptureContext.createForTesting(),
            vocabulary(),
            new LinkedHashMap<>(),
            "base.png",
            "lighting.png",
            "fog.png",
            "overlay.png",
            "front.png",
            0,
            0,
            0,
            0,
            0,
            CameraOverlayRetentionResult.noOverlayStage(640, 360),
            new LinkedHashMap<>()
        );
    }

    @Test
    public void marksUnsupportedBaseSourcesAsPartialRatherThanMeasuredDarkness() {
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
        String json = TranslucentProbeMetadata.toJson(
            640,
            360,
            "fast",
            "none",
            Arrays.asList("vanilla"),
            FogRetentionCaptureContext.createForTesting(
                FogRetentionParameters.createForTesting(
                    FogRetentionParameters.Mode.LINEAR,
                    FogRetentionParameters.DistanceMode.STANDARD_EYE_PLANE_ABSOLUTE,
                    0.0F,
                    24.0F,
                    32.0F
                )
            ),
            LightingRetentionCaptureContext.createForTesting(),
            vocabulary(),
            artifacts,
            "base.png",
            "lighting.png",
            "fog.png",
            "overlay.png",
            "front.png",
            11,
            1,
            12,
            0,
            0,
            CameraOverlayRetentionResult.noOverlayStage(640, 360),
            new LinkedHashMap<>()
        );

        assertTrue(json.contains("\"status\": \"partial\""));
        assertTrue(json.contains("\"unsupportedBasePixelCount\": 1"));
        assertTrue(json.contains("not a measured dark value"));
    }

    private static CameraOverlayRetentionResult overlayResult() {
        byte[] retention = new byte[640 * 360];
        Arrays.fill(retention, (byte)230);
        EnumMap<CameraOverlayKind, Integer> counts = new EnumMap<>(CameraOverlayKind.class);
        counts.put(CameraOverlayKind.UNDERWATER, 1);
        return new CameraOverlayRetentionResult(640, 360, retention, true, counts);
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
