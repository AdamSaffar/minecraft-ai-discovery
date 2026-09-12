package io.github.minecraftaidiscovery.semantic.lighting;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public final class LightingRetentionCaptureContext {
    private final float tickDelta;
    private final double gamma;
    private final boolean ordinaryGammaRange;
    private final boolean ambientOcclusionEnabled;
    private final String world;
    private final String dimensionType;
    private final boolean hasSkyLight;
    private final boolean hasFixedTime;
    private final boolean darkenedDirectionalLighting;
    private final boolean endLightmapTransform;
    private final long worldTime;
    private final long timeOfDay;
    private final float rainGradient;
    private final float thunderGradient;
    private final int lightningTicksLeft;
    private final float skyDarkness;
    private final float underwaterVisibility;
    private final boolean nightVision;
    private final int nightVisionDurationTicks;
    private final float nightVisionStrength;
    private final boolean conduitPower;
    private final int conduitPowerDurationTicks;

    private LightingRetentionCaptureContext(
        float tickDelta,
        double gamma,
        boolean ambientOcclusionEnabled,
        String world,
        String dimensionType,
        boolean hasSkyLight,
        boolean hasFixedTime,
        boolean darkenedDirectionalLighting,
        boolean endLightmapTransform,
        long worldTime,
        long timeOfDay,
        float rainGradient,
        float thunderGradient,
        int lightningTicksLeft,
        float skyDarkness,
        float underwaterVisibility,
        boolean nightVision,
        int nightVisionDurationTicks,
        float nightVisionStrength,
        boolean conduitPower,
        int conduitPowerDurationTicks
    ) {
        requireFinite("tick delta", tickDelta);
        if (!Double.isFinite(gamma)) {
            throw new IllegalArgumentException("Lighting-retention gamma must be finite");
        }
        requireText("world", world);
        requireText("dimension type", dimensionType);
        requireUnit("rain gradient", rainGradient);
        requireUnit("thunder gradient", thunderGradient);
        requireUnit("sky darkness", skyDarkness);
        requireUnit("underwater visibility", underwaterVisibility);
        requireUnit("night-vision strength", nightVisionStrength);
        if (lightningTicksLeft < 0) {
            throw new IllegalArgumentException("Lightning ticks must not be negative");
        }
        if (nightVision != (nightVisionDurationTicks >= 0)
            || conduitPower != (conduitPowerDurationTicks >= 0)) {
            throw new IllegalArgumentException("Lighting status duration does not match its flag");
        }
        if (!nightVision && nightVisionStrength != 0.0F) {
            throw new IllegalArgumentException("Night-vision strength exists without the effect");
        }
        this.tickDelta = tickDelta;
        this.gamma = gamma;
        this.ordinaryGammaRange = gamma >= 0.0 && gamma <= 1.0;
        this.ambientOcclusionEnabled = ambientOcclusionEnabled;
        this.world = world;
        this.dimensionType = dimensionType;
        this.hasSkyLight = hasSkyLight;
        this.hasFixedTime = hasFixedTime;
        this.darkenedDirectionalLighting = darkenedDirectionalLighting;
        this.endLightmapTransform = endLightmapTransform;
        this.worldTime = worldTime;
        this.timeOfDay = timeOfDay;
        this.rainGradient = rainGradient;
        this.thunderGradient = thunderGradient;
        this.lightningTicksLeft = lightningTicksLeft;
        this.skyDarkness = skyDarkness;
        this.underwaterVisibility = underwaterVisibility;
        this.nightVision = nightVision;
        this.nightVisionDurationTicks = nightVisionDurationTicks;
        this.nightVisionStrength = nightVisionStrength;
        this.conduitPower = conduitPower;
        this.conduitPowerDurationTicks = conduitPowerDurationTicks;
    }

    public static LightingRetentionCaptureContext capture(
        MinecraftClient client,
        GameRenderer gameRenderer,
        float tickDelta
    ) {
        if (client == null || client.world == null || client.player == null || gameRenderer == null) {
            throw new IllegalArgumentException("Lighting-retention capture input is missing");
        }
        String graphicsMode = client.options.graphicsMode.toString();
        if (!"fast".equals(graphicsMode) && !"fancy".equals(graphicsMode)) {
            throw new IllegalStateException(
                "Lighting-retention capture supports only Fast or Fancy graphics"
            );
        }
        if (gameRenderer.getShader() != null) {
            throw new IllegalStateException(
                "Lighting-retention capture does not support post shaders"
            );
        }

        ClientPlayerEntity player = client.player;
        StatusEffectInstance nightVision = player.getStatusEffect(StatusEffects.NIGHT_VISION);
        StatusEffectInstance conduit = player.getStatusEffect(StatusEffects.CONDUIT_POWER);
        return new LightingRetentionCaptureContext(
            tickDelta,
            client.options.gamma,
            MinecraftClient.isAmbientOcclusionEnabled(),
            client.world.getRegistryKey().getValue().toString(),
            client.world.getDimensionRegistryKey().getValue().toString(),
            client.world.getDimension().hasSkyLight(),
            client.world.getDimension().hasFixedTime(),
            client.world.getSkyProperties().isDarkened(),
            client.world.getSkyProperties().shouldRenderSky(),
            client.world.getTime(),
            client.world.getTimeOfDay(),
            client.world.getRainGradient(tickDelta),
            client.world.getThunderGradient(tickDelta),
            client.world.getLightningTicksLeft(),
            gameRenderer.getSkyDarkness(tickDelta),
            player.getUnderwaterVisibility(),
            nightVision != null,
            nightVision == null ? -1 : nightVision.getDuration(),
            nightVision == null ? 0.0F : GameRenderer.getNightVisionStrength(player, tickDelta),
            conduit != null,
            conduit == null ? -1 : conduit.getDuration()
        );
    }

    public static LightingRetentionCaptureContext createForTesting() {
        return createForTesting(0.75);
    }

    public static LightingRetentionCaptureContext createForTesting(double gamma) {
        return new LightingRetentionCaptureContext(
            0.5F,
            gamma,
            true,
            "minecraft:overworld",
            "minecraft:overworld",
            true,
            false,
            false,
            false,
            6000L,
            6000L,
            0.25F,
            0.0F,
            0,
            0.0F,
            0.4F,
            true,
            300,
            1.0F,
            false,
            -1
        );
    }

    public float getTickDelta() {
        return tickDelta;
    }

    public double getGamma() {
        return gamma;
    }

    public boolean isOrdinaryGammaRange() {
        return ordinaryGammaRange;
    }

    public boolean isAmbientOcclusionEnabled() {
        return ambientOcclusionEnabled;
    }

    public String getWorld() {
        return world;
    }

    public String getDimensionType() {
        return dimensionType;
    }

    public boolean hasSkyLight() {
        return hasSkyLight;
    }

    public boolean hasFixedTime() {
        return hasFixedTime;
    }

    public boolean hasDarkenedDirectionalLighting() {
        return darkenedDirectionalLighting;
    }

    public boolean hasEndLightmapTransform() {
        return endLightmapTransform;
    }

    public long getWorldTime() {
        return worldTime;
    }

    public long getTimeOfDay() {
        return timeOfDay;
    }

    public float getRainGradient() {
        return rainGradient;
    }

    public float getThunderGradient() {
        return thunderGradient;
    }

    public int getLightningTicksLeft() {
        return lightningTicksLeft;
    }

    public float getSkyDarkness() {
        return skyDarkness;
    }

    public float getUnderwaterVisibility() {
        return underwaterVisibility;
    }

    public boolean hasNightVision() {
        return nightVision;
    }

    public int getNightVisionDurationTicks() {
        return nightVisionDurationTicks;
    }

    public float getNightVisionStrength() {
        return nightVisionStrength;
    }

    public boolean hasConduitPower() {
        return conduitPower;
    }

    public int getConduitPowerDurationTicks() {
        return conduitPowerDurationTicks;
    }

    private static void requireUnit(String name, float value) {
        requireFinite(name, value);
        if (value < 0.0F || value > 1.0F) {
            throw new IllegalArgumentException("Lighting-retention " + name + " is outside [0,1]");
        }
    }

    private static void requireFinite(String name, float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Lighting-retention " + name + " must be finite");
        }
    }

    private static void requireText(String name, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Lighting-retention " + name + " is missing");
        }
    }
}
