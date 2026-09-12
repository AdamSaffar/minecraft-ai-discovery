package io.github.minecraftaidiscovery.semantic.translucent;

import java.nio.FloatBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;

public final class FogRetentionCaptureContext {
    private final FogRetentionParameters parameters;
    private final float underwaterVisibility;
    private final float biomeDensityModifier;
    private final int viewDistanceChunks;
    private final float terrainFogViewDistance;
    private final String perspective;
    private final boolean thickFog;
    private final boolean skyFogApplied;
    private final boolean blindness;
    private final int blindnessDurationTicks;
    private final boolean fireResistance;
    private final boolean nausea;
    private final boolean nightVision;
    private final float[] projectionMatrix;

    private FogRetentionCaptureContext(
        FogRetentionParameters parameters,
        float underwaterVisibility,
        float biomeDensityModifier,
        int viewDistanceChunks,
        float terrainFogViewDistance,
        String perspective,
        boolean thickFog,
        boolean skyFogApplied,
        boolean blindness,
        int blindnessDurationTicks,
        boolean fireResistance,
        boolean nausea,
        boolean nightVision,
        float[] projectionMatrix
    ) {
        if (parameters == null || perspective == null || projectionMatrix == null) {
            throw new IllegalArgumentException("Fog retention capture context is incomplete");
        }
        if (underwaterVisibility < 0.0F || underwaterVisibility > 1.0F) {
            throw new IllegalArgumentException("Underwater visibility is outside [0,1]");
        }
        if (biomeDensityModifier != 0.0F && biomeDensityModifier != 0.005F) {
            throw new IllegalArgumentException("Unexpected water biome fog modifier");
        }
        if (viewDistanceChunks < 2 || terrainFogViewDistance <= 0.0F) {
            throw new IllegalArgumentException("Fog view distance is invalid");
        }
        if (blindness != (blindnessDurationTicks >= 0)) {
            throw new IllegalArgumentException("Blindness duration does not match its status flag");
        }
        if (projectionMatrix.length != 16) {
            throw new IllegalArgumentException("Projection matrix must contain 16 values");
        }
        for (float value : projectionMatrix) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("Projection matrix must be finite");
            }
        }
        this.parameters = parameters;
        this.underwaterVisibility = underwaterVisibility;
        this.biomeDensityModifier = biomeDensityModifier;
        this.viewDistanceChunks = viewDistanceChunks;
        this.terrainFogViewDistance = terrainFogViewDistance;
        this.perspective = perspective;
        this.thickFog = thickFog;
        this.skyFogApplied = skyFogApplied;
        this.blindness = blindness;
        this.blindnessDurationTicks = blindnessDurationTicks;
        this.fireResistance = fireResistance;
        this.nausea = nausea;
        this.nightVision = nightVision;
        this.projectionMatrix = projectionMatrix.clone();
    }

    public static FogRetentionCaptureContext capture(
        MinecraftClient client,
        Camera camera,
        GameRenderer gameRenderer,
        Matrix4f projectionMatrix
    ) {
        if (client == null
            || client.world == null
            || camera == null
            || gameRenderer == null
            || projectionMatrix == null) {
            throw new IllegalArgumentException("Fog retention capture input is missing");
        }
        String graphicsMode = client.options.graphicsMode.toString();
        if (!"fast".equals(graphicsMode) && !"fancy".equals(graphicsMode)) {
            throw new IllegalStateException(
                "Fog retention capture supports only Fast or Fancy graphics"
            );
        }
        if (gameRenderer.getShader() != null) {
            throw new IllegalStateException("Fog retention capture does not support post shaders");
        }
        if (camera.isThirdPerson() != (client.options.perspective > 0)) {
            throw new IllegalStateException("Camera perspective changed during fog capture");
        }

        LivingEntity focused = camera.getFocusedEntity() instanceof LivingEntity
            ? (LivingEntity)camera.getFocusedEntity()
            : null;
        ClientPlayerEntity focusedPlayer = camera.getFocusedEntity() instanceof ClientPlayerEntity
            ? (ClientPlayerEntity)camera.getFocusedEntity()
            : null;
        StatusEffectInstance blindnessEffect = focused == null
            ? null
            : focused.getStatusEffect(StatusEffects.BLINDNESS);
        float underwaterVisibility = focusedPlayer == null
            ? 0.0F
            : focusedPlayer.getUnderwaterVisibility();
        float biomeDensityModifier = 0.0F;
        if (focusedPlayer != null
            && camera.getSubmergedFluidState().isIn(FluidTags.WATER)) {
            Biome biome = focusedPlayer.world.getBiome(focusedPlayer.getBlockPos());
            if (biome == Biomes.SWAMP || biome == Biomes.SWAMP_HILLS) {
                biomeDensityModifier = 0.005F;
            }
        }

        // WorldRenderer 1.16.1 passes camera X and Y to this check, so mirror that call exactly.
        boolean thickFog = client.world.getSkyProperties().useThickFog(
            MathHelper.floor(camera.getPos().x),
            MathHelper.floor(camera.getPos().y)
        ) || client.inGameHud.getBossBarHud().shouldThickenFog();
        float terrainFogViewDistance = Math.max(gameRenderer.getViewDistance() - 16.0F, 32.0F);
        FogRetentionParameters parameters = FogRetentionParameters.captureAtTerrainHook();
        requireMinecraftFogParameters(
            parameters,
            camera,
            focused,
            blindnessEffect,
            underwaterVisibility,
            biomeDensityModifier,
            thickFog,
            terrainFogViewDistance
        );
        return new FogRetentionCaptureContext(
            parameters,
            underwaterVisibility,
            biomeDensityModifier,
            client.options.viewDistance,
            terrainFogViewDistance,
            perspectiveName(client.options.perspective),
            thickFog,
            client.options.viewDistance >= 4,
            blindnessEffect != null,
            blindnessEffect == null ? -1 : blindnessEffect.getDuration(),
            focused != null && focused.hasStatusEffect(StatusEffects.FIRE_RESISTANCE),
            focused != null && focused.hasStatusEffect(StatusEffects.NAUSEA),
            focused != null && focused.hasStatusEffect(StatusEffects.NIGHT_VISION),
            projectionValues(projectionMatrix)
        );
    }

    static FogRetentionCaptureContext createForTesting(FogRetentionParameters parameters) {
        return new FogRetentionCaptureContext(
            parameters,
            0.4F,
            0.005F,
            12,
            176.0F,
            "first_person",
            false,
            true,
            false,
            -1,
            false,
            false,
            true,
            new float[] {
                1.0F, 0.0F, 0.0F, 0.0F,
                0.0F, 1.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 1.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F
            }
        );
    }

    public FogRetentionParameters getParameters() {
        return parameters;
    }

    public float getUnderwaterVisibility() {
        return underwaterVisibility;
    }

    public float getBiomeDensityModifier() {
        return biomeDensityModifier;
    }

    public int getViewDistanceChunks() {
        return viewDistanceChunks;
    }

    public float getTerrainFogViewDistance() {
        return terrainFogViewDistance;
    }

    public String getPerspective() {
        return perspective;
    }

    public boolean isThickFog() {
        return thickFog;
    }

    public boolean isSkyFogApplied() {
        return skyFogApplied;
    }

    public boolean hasBlindness() {
        return blindness;
    }

    public int getBlindnessDurationTicks() {
        return blindnessDurationTicks;
    }

    public boolean hasFireResistance() {
        return fireResistance;
    }

    public boolean hasNausea() {
        return nausea;
    }

    public boolean hasNightVision() {
        return nightVision;
    }

    public float[] copyProjectionMatrix() {
        return projectionMatrix.clone();
    }

    private static String perspectiveName(int perspective) {
        switch (perspective) {
            case 0:
                return "first_person";
            case 1:
                return "third_person_back";
            case 2:
                return "third_person_front";
            default:
                throw new IllegalStateException("Unsupported camera perspective: " + perspective);
        }
    }

    private static float[] projectionValues(Matrix4f matrix) {
        FloatBuffer buffer = FloatBuffer.allocate(16);
        matrix.writeToBuffer(buffer);
        float[] values = new float[16];
        for (int index = 0; index < values.length; index++) {
            values[index] = buffer.get(index);
        }
        return values;
    }

    private static void requireMinecraftFogParameters(
        FogRetentionParameters parameters,
        Camera camera,
        LivingEntity focused,
        StatusEffectInstance blindnessEffect,
        float underwaterVisibility,
        float biomeDensityModifier,
        boolean thickFog,
        float viewDistance
    ) {
        if (camera.getSubmergedFluidState().isIn(FluidTags.WATER)) {
            requireMode(parameters, FogRetentionParameters.Mode.EXP2);
            float expectedDensity = 0.05F
                - underwaterVisibility * underwaterVisibility * 0.03F
                + biomeDensityModifier;
            requireClose("water fog density", expectedDensity, parameters.getDensity());
            return;
        }

        requireMode(parameters, FogRetentionParameters.Mode.LINEAR);
        float expectedStart;
        float expectedEnd;
        if (camera.getSubmergedFluidState().isIn(FluidTags.LAVA)) {
            boolean fireResistance = focused != null
                && focused.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
            expectedStart = fireResistance ? 0.0F : 0.25F;
            expectedEnd = fireResistance ? 3.0F : 1.0F;
        } else if (blindnessEffect != null) {
            float range = MathHelper.lerp(
                Math.min(1.0F, blindnessEffect.getDuration() / 20.0F),
                viewDistance,
                5.0F
            );
            expectedStart = range * 0.25F;
            expectedEnd = range;
        } else if (thickFog) {
            expectedStart = viewDistance * 0.05F;
            expectedEnd = Math.min(viewDistance, 192.0F) * 0.5F;
        } else {
            expectedStart = viewDistance * 0.75F;
            expectedEnd = viewDistance;
        }
        requireClose("fog start", expectedStart, parameters.getStart());
        requireClose("fog end", expectedEnd, parameters.getEnd());
    }

    private static void requireMode(
        FogRetentionParameters parameters,
        FogRetentionParameters.Mode expected
    ) {
        if (parameters.getMode() != expected) {
            throw new IllegalStateException(
                "Minecraft fog mode does not match the camera medium"
            );
        }
    }

    private static void requireClose(String name, float expected, float actual) {
        if (Math.abs(expected - actual) > 0.000001F) {
            throw new IllegalStateException(
                "Minecraft " + name + " does not match the expected terrain fog state"
            );
        }
    }
}
