package io.github.minecraftaidiscovery.semantic.translucent;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

public final class FogRetentionParameters {
    private static final int FOG_DISTANCE_MODE_NV = 34138;
    private static final int EYE_RADIAL_NV = 34139;
    private static final int EYE_PLANE_ABSOLUTE_NV = 34140;

    private final Mode mode;
    private final DistanceMode distanceMode;
    private final boolean nvFogDistanceSupported;
    private final float density;
    private final float start;
    private final float end;

    private FogRetentionParameters(
        Mode mode,
        DistanceMode distanceMode,
        boolean nvFogDistanceSupported,
        float density,
        float start,
        float end
    ) {
        if (mode == null || distanceMode == null) {
            throw new IllegalArgumentException("Fog mode is missing");
        }
        requireFinite("density", density);
        requireFinite("start", start);
        requireFinite("end", end);
        if (mode == Mode.EXP2 && density < 0.0F) {
            throw new IllegalArgumentException("Fog density must not be negative");
        }
        if (mode == Mode.LINEAR && end <= start) {
            throw new IllegalArgumentException("Linear fog end must be greater than its start");
        }
        this.mode = mode;
        this.distanceMode = distanceMode;
        this.nvFogDistanceSupported = nvFogDistanceSupported;
        this.density = density;
        this.start = start;
        this.end = end;
    }

    public static FogRetentionParameters captureAtTerrainHook() {
        // The hook runs after cutout.endDrawing(), which should have disabled fog without changing its parameters.
        if (GL11.glIsEnabled(GL11.GL_FOG)) {
            throw new IllegalStateException("Fog is unexpectedly enabled at the terrain capture hook");
        }
        return captureCurrent();
    }

    static FogRetentionParameters createForTesting(
        Mode mode,
        DistanceMode distanceMode,
        float density,
        float start,
        float end
    ) {
        return new FogRetentionParameters(mode, distanceMode, false, density, start, end);
    }

    void requireCurrentTerrainState() {
        FogRetentionParameters current = captureCurrent();
        if (!sameState(current)) {
            throw new IllegalStateException("Minecraft fog parameters changed during diagnostic replay");
        }
    }

    public float retention(float fogDistance) {
        requireFinite("fog distance", fogDistance);
        if (fogDistance < 0.0F) {
            throw new IllegalArgumentException("Fog distance must not be negative");
        }
        if (mode == Mode.EXP2) {
            float scaled = density * fogDistance;
            return (float)Math.exp(-(scaled * scaled));
        }
        return Math.max(0.0F, Math.min(1.0F, (end - fogDistance) / (end - start)));
    }

    public Mode getMode() {
        return mode;
    }

    public DistanceMode getDistanceMode() {
        return distanceMode;
    }

    public boolean isNvFogDistanceSupported() {
        return nvFogDistanceSupported;
    }

    public float getDensity() {
        return density;
    }

    public float getStart() {
        return start;
    }

    public float getEnd() {
        return end;
    }

    private static FogRetentionParameters captureCurrent() {
        Mode mode = Mode.fromOpenGl(GL11.glGetInteger(GL11.GL_FOG_MODE));
        boolean nvSupported = GL.getCapabilities().GL_NV_fog_distance;
        DistanceMode distanceMode = nvSupported
            ? DistanceMode.fromNvOpenGl(GL11.glGetInteger(FOG_DISTANCE_MODE_NV))
            : DistanceMode.STANDARD_EYE_PLANE_ABSOLUTE;
        return new FogRetentionParameters(
            mode,
            distanceMode,
            nvSupported,
            GL11.glGetFloat(GL11.GL_FOG_DENSITY),
            GL11.glGetFloat(GL11.GL_FOG_START),
            GL11.glGetFloat(GL11.GL_FOG_END)
        );
    }

    private boolean sameState(FogRetentionParameters other) {
        return mode == other.mode
            && distanceMode == other.distanceMode
            && nvFogDistanceSupported == other.nvFogDistanceSupported
            && sameFloat(density, other.density)
            && sameFloat(start, other.start)
            && sameFloat(end, other.end);
    }

    private static boolean sameFloat(float left, float right) {
        return Float.floatToIntBits(left) == Float.floatToIntBits(right);
    }

    private static void requireFinite(String name, float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("Fog " + name + " must be finite");
        }
    }

    public enum Mode {
        LINEAR(GL11.GL_LINEAR, "linear"),
        EXP2(GL11.GL_EXP2, "exp2");

        private final int openGlValue;
        private final String metadataName;

        Mode(int openGlValue, String metadataName) {
            this.openGlValue = openGlValue;
            this.metadataName = metadataName;
        }

        public String getMetadataName() {
            return metadataName;
        }

        private static Mode fromOpenGl(int value) {
            for (Mode mode : values()) {
                if (mode.openGlValue == value) {
                    return mode;
                }
            }
            throw new IllegalStateException("Unsupported OpenGL fog mode: " + value);
        }
    }

    public enum DistanceMode {
        STANDARD_EYE_PLANE_ABSOLUTE("standard_eye_plane_absolute"),
        NV_EYE_RADIAL("nv_eye_radial"),
        NV_EYE_PLANE_ABSOLUTE("nv_eye_plane_absolute");

        private final String metadataName;

        DistanceMode(String metadataName) {
            this.metadataName = metadataName;
        }

        public String getMetadataName() {
            return metadataName;
        }

        private static DistanceMode fromNvOpenGl(int value) {
            if (value == EYE_RADIAL_NV) {
                return NV_EYE_RADIAL;
            }
            if (value == EYE_PLANE_ABSOLUTE_NV) {
                return NV_EYE_PLANE_ABSOLUTE;
            }
            throw new IllegalStateException("Unsupported NV fog distance mode: " + value);
        }
    }
}
