package io.github.minecraftaidiscovery.semantic;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

public final class SemanticGpuTransport {
    private SemanticGpuTransport() {
    }

    public static void draw(
        SemanticRenderLayer layer,
        int semanticBufferId,
        boolean backgroundOnly,
        int vertexCount,
        Matrix4f matrix,
        int mode
    ) {
        if (layer == null) {
            throw new IllegalArgumentException("Semantic render layer is missing");
        }
        if (!backgroundOnly && semanticBufferId < 0) {
            throw new IllegalStateException("Vertex buffer has no semantic sidecar");
        }
        if (mode != GL11.GL_QUADS || vertexCount % 4 != 0) {
            throw new IllegalStateException("Semantic draw is not composed of complete quads");
        }

        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        boolean blockTexture = textureEnabled(GL13.GL_TEXTURE0);
        boolean auxiliaryTexture = textureEnabled(GL13.GL_TEXTURE1);
        boolean lightmapTexture = textureEnabled(GL13.GL_TEXTURE2);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
        boolean colorArray = GL11.glIsEnabled(GL11.GL_COLOR_ARRAY);
        boolean dither = GL11.glIsEnabled(GL11.GL_DITHER);
        boolean colorLogic = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
        boolean polygonSmooth = GL11.glIsEnabled(GL11.GL_POLYGON_SMOOTH);
        boolean multisample = GL11.glIsEnabled(GL13.GL_MULTISAMPLE);
        boolean sampleAlphaToCoverage = GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
        TextureEnvironmentState textureEnvironment = layer.usesTextureCoverage()
            ? TextureEnvironmentState.capture()
            : null;

        try {
            setTextureUnit(GL13.GL_TEXTURE1, false);
            setTextureUnit(GL13.GL_TEXTURE2, false);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.disableLighting();
            RenderSystem.disableFog();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            GL11.glDisable(GL11.GL_DITHER);
            GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
            GL11.glDisable(GL13.GL_MULTISAMPLE);
            GL11.glDisable(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);

            if (layer.usesTextureCoverage()) {
                setTextureUnit(GL13.GL_TEXTURE0, true);
                configureCutoutTextureEnvironment();
                RenderSystem.enableAlphaTest();
                RenderSystem.alphaFunc(GL11.GL_GREATER, SemanticClassEncoding.CUTOUT_ALPHA_THRESHOLD);
            } else {
                setTextureUnit(GL13.GL_TEXTURE0, false);
                RenderSystem.disableAlphaTest();
            }

            if (backgroundOnly) {
                GlStateManager.disableClientState(GL11.GL_COLOR_ARRAY);
                GlStateManager.color4f(0.0F, 0.0F, 0.0F, 1.0F);
            } else {
                RenderSystem.glBindBuffer(GL15.GL_ARRAY_BUFFER, () -> semanticBufferId);
                GlStateManager.colorPointer(
                    4,
                    GL11.GL_UNSIGNED_BYTE,
                    SemanticVertexData.BYTES_PER_VERTEX,
                    0L
                );
                GlStateManager.enableClientState(GL11.GL_COLOR_ARRAY);
            }

            RenderSystem.pushMatrix();
            try {
                RenderSystem.loadIdentity();
                RenderSystem.multMatrix(matrix);
                RenderSystem.drawArrays(mode, 0, vertexCount);
            } finally {
                RenderSystem.popMatrix();
            }
        } finally {
            RenderSystem.glBindBuffer(GL15.GL_ARRAY_BUFFER, () -> arrayBuffer);
            setClientState(GL11.GL_COLOR_ARRAY, colorArray);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            if (textureEnvironment != null) {
                textureEnvironment.restore();
            }
            RenderSystem.alphaFunc(alphaFunction, alphaReference);
            restoreManagedState(lighting, fog, blend, alphaTest, depthTest);
            RenderSystem.depthMask(depthMask);
            RenderSystem.colorMask(
                colorMask.get(0) != 0,
                colorMask.get(1) != 0,
                colorMask.get(2) != 0,
                colorMask.get(3) != 0
            );
            setTextureUnit(GL13.GL_TEXTURE2, lightmapTexture);
            setTextureUnit(GL13.GL_TEXTURE1, auxiliaryTexture);
            setTextureUnit(GL13.GL_TEXTURE0, blockTexture);
            restoreCapability(GL11.GL_DITHER, dither);
            restoreCapability(GL11.GL_COLOR_LOGIC_OP, colorLogic);
            restoreCapability(GL11.GL_POLYGON_SMOOTH, polygonSmooth);
            restoreCapability(GL13.GL_MULTISAMPLE, multisample);
            restoreCapability(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE, sampleAlphaToCoverage);
            RenderSystem.activeTexture(activeTexture);
        }
    }

    private static void configureCutoutTextureEnvironment() {
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL13.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvf(GL11.GL_TEXTURE_ENV, GL13.GL_RGB_SCALE, 1.0F);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        GL11.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_ALPHA_SCALE, 1.0F);
    }

    private static void restoreManagedState(
        boolean lighting,
        boolean fog,
        boolean blend,
        boolean alphaTest,
        boolean depthTest
    ) {
        setLighting(lighting);
        setFog(fog);
        setBlend(blend);
        setAlphaTest(alphaTest);
        setDepthTest(depthTest);
    }

    private static boolean textureEnabled(int textureUnit) {
        int previousTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        RenderSystem.activeTexture(textureUnit);
        boolean enabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        RenderSystem.activeTexture(previousTexture);
        return enabled;
    }

    private static void setTextureUnit(int textureUnit, boolean enabled) {
        RenderSystem.activeTexture(textureUnit);
        if (enabled) {
            RenderSystem.enableTexture();
        } else {
            RenderSystem.disableTexture();
        }
    }

    private static void setClientState(int capability, boolean enabled) {
        if (enabled) {
            GlStateManager.enableClientState(capability);
        } else {
            GlStateManager.disableClientState(capability);
        }
    }

    private static void setLighting(boolean enabled) {
        if (enabled) {
            RenderSystem.enableLighting();
        } else {
            RenderSystem.disableLighting();
        }
    }

    private static void setFog(boolean enabled) {
        if (enabled) {
            RenderSystem.enableFog();
        } else {
            RenderSystem.disableFog();
        }
    }

    private static void setBlend(boolean enabled) {
        if (enabled) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
    }

    private static void setAlphaTest(boolean enabled) {
        if (enabled) {
            RenderSystem.enableAlphaTest();
        } else {
            RenderSystem.disableAlphaTest();
        }
    }

    private static void setDepthTest(boolean enabled) {
        if (enabled) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
    }

    private static void restoreCapability(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    private static final class TextureEnvironmentState {
        private final int mode;
        private final int combineRgb;
        private final int source0Rgb;
        private final int operand0Rgb;
        private final float rgbScale;
        private final int combineAlpha;
        private final int source0Alpha;
        private final int operand0Alpha;
        private final float alphaScale;

        private TextureEnvironmentState(
            int mode,
            int combineRgb,
            int source0Rgb,
            int operand0Rgb,
            float rgbScale,
            int combineAlpha,
            int source0Alpha,
            int operand0Alpha,
            float alphaScale
        ) {
            this.mode = mode;
            this.combineRgb = combineRgb;
            this.source0Rgb = source0Rgb;
            this.operand0Rgb = operand0Rgb;
            this.rgbScale = rgbScale;
            this.combineAlpha = combineAlpha;
            this.source0Alpha = source0Alpha;
            this.operand0Alpha = operand0Alpha;
            this.alphaScale = alphaScale;
        }

        private static TextureEnvironmentState capture() {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            return new TextureEnvironmentState(
                GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE),
                GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB),
                GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB),
                GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB),
                GL11.glGetTexEnvf(GL11.GL_TEXTURE_ENV, GL13.GL_RGB_SCALE),
                GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA),
                GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA),
                GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA),
                GL11.glGetTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_ALPHA_SCALE)
            );
        }

        private void restore() {
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, mode);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, combineRgb);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, source0Rgb);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, operand0Rgb);
            GL11.glTexEnvf(GL11.GL_TEXTURE_ENV, GL13.GL_RGB_SCALE, rgbScale);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, combineAlpha);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, source0Alpha);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, operand0Alpha);
            GL11.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_ALPHA_SCALE, alphaScale);
        }
    }
}
