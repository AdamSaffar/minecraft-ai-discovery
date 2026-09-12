package io.github.minecraftaidiscovery.semantic.lighting;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.minecraftaidiscovery.semantic.SemanticClassEncoding;
import io.github.minecraftaidiscovery.semantic.SemanticRenderLayer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class LightingRetentionGpuTransport {
    private LightingRetentionGpuTransport() {
    }

    public static void draw(
        LightingRetentionRenderPass.Context context,
        int sidecarBufferId,
        boolean invalidOnly,
        int vertexCount,
        Matrix4f matrix,
        int drawMode
    ) {
        if (!invalidOnly && sidecarBufferId < 0) {
            throw new IllegalStateException("Vertex buffer has no lighting-retention sidecar");
        }
        if (drawMode != GL11.GL_QUADS || vertexCount % 4 != 0) {
            throw new IllegalStateException("Lighting-retention draw is not composed of complete quads");
        }

        context.getTarget().beginWrite(false);
        requireRendererLightmapState();
        GlState state = GlState.capture(context.getLayer());
        try {
            configureState(context.getLayer());
            if (invalidOnly) {
                GlStateManager.disableClientState(GL11.GL_COLOR_ARRAY);
                GlStateManager.color4f(0.0F, 0.0F, 0.0F, 0.0F);
            } else {
                RenderSystem.glBindBuffer(GL15.GL_ARRAY_BUFFER, () -> sidecarBufferId);
                GlStateManager.colorPointer(
                    4,
                    GL11.GL_UNSIGNED_BYTE,
                    LightingRetentionVertexData.BYTES_PER_VERTEX,
                    0L
                );
                GlStateManager.enableClientState(GL11.GL_COLOR_ARRAY);
            }

            RenderSystem.pushMatrix();
            try {
                RenderSystem.loadIdentity();
                RenderSystem.multMatrix(matrix);
                RenderSystem.drawArrays(drawMode, 0, vertexCount);
            } finally {
                RenderSystem.popMatrix();
            }
        } finally {
            state.restore();
        }
    }

    private static void requireRendererLightmapState() {
        if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0) {
            throw new IllegalStateException(
                "Lighting-retention capture does not support an active terrain shader"
            );
        }
        if (GL11.glGetInteger(GL11.GL_SHADE_MODEL) != GL11.GL_SMOOTH) {
            throw new IllegalStateException(
                "Lighting-retention capture requires Minecraft's smooth terrain interpolation"
            );
        }

        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        RenderSystem.activeTexture(GL13.GL_TEXTURE2);
        boolean enabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int environment = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE);
        int minFilter = texture == 0
            ? -1
            : GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        int magFilter = texture == 0
            ? -1
            : GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
        RenderSystem.activeTexture(activeTexture);

        int clientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        GlStateManager.clientActiveTexture(GL13.GL_TEXTURE2);
        boolean coordinates = GL11.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY);
        GlStateManager.clientActiveTexture(clientTexture);

        if (!enabled
            || texture == 0
            || environment != GL11.GL_MODULATE
            || minFilter != GL11.GL_LINEAR
            || magFilter != GL11.GL_LINEAR
            || !coordinates) {
            throw new IllegalStateException(
                "Minecraft's active lightmap state is unsupported or incomplete"
            );
        }
    }

    private static void configureState(SemanticRenderLayer layer) {
        GL20.glUseProgram(0);
        setTextureUnit(GL13.GL_TEXTURE1, false);
        setTextureUnit(GL13.GL_TEXTURE2, true);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.disableLighting();
        RenderSystem.disableFog();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        // The copied semantic depth admits only the same winning base surface.
        RenderSystem.depthFunc(GL11.GL_EQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.colorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_DITHER);
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GL11.glDisable(GL13.GL_MULTISAMPLE);
        GL11.glDisable(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
        setFramebufferSrgb(false);

        if (layer.usesTextureCoverage()) {
            setTextureUnit(GL13.GL_TEXTURE0, true);
            configureCutoutTextureEnvironment();
        } else {
            setTextureUnit(GL13.GL_TEXTURE0, false);
        }
        // Invalid sources have zero sidecar alpha; cutout sources additionally retain atlas alpha.
        RenderSystem.enableAlphaTest();
        RenderSystem.alphaFunc(GL11.GL_GREATER, SemanticClassEncoding.CUTOUT_ALPHA_THRESHOLD);
    }

    private static void configureCutoutTextureEnvironment() {
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL13.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvf(GL11.GL_TEXTURE_ENV, GL13.GL_RGB_SCALE, 1.0F);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL13.GL_PREVIOUS);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);
        GL11.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_ALPHA_SCALE, 1.0F);
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

    private static boolean framebufferSrgbSupported() {
        return GL.getCapabilities().OpenGL30
            || GL.getCapabilities().GL_ARB_framebuffer_sRGB
            || GL.getCapabilities().GL_EXT_framebuffer_sRGB;
    }

    private static void setFramebufferSrgb(boolean enabled) {
        if (!framebufferSrgbSupported()) {
            return;
        }
        if (enabled) {
            GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
        } else {
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
        }
    }

    private static final class GlState {
        private final int activeTexture;
        private final boolean blockTexture;
        private final boolean auxiliaryTexture;
        private final boolean lightmapTexture;
        private final int arrayBuffer;
        private final int program;
        private final boolean colorArray;
        private final FloatBuffer currentColor;
        private final boolean lighting;
        private final boolean fog;
        private final boolean alphaTest;
        private final int alphaFunction;
        private final float alphaReference;
        private final boolean blend;
        private final int blendSourceRgb;
        private final int blendDestinationRgb;
        private final int blendSourceAlpha;
        private final int blendDestinationAlpha;
        private final boolean depthTest;
        private final int depthFunction;
        private final boolean depthMask;
        private final ByteBuffer colorMask;
        private final boolean dither;
        private final boolean colorLogic;
        private final boolean polygonSmooth;
        private final boolean multisample;
        private final boolean sampleAlphaToCoverage;
        private final boolean framebufferSrgb;
        private final TextureEnvironmentState textureEnvironment;

        private GlState(SemanticRenderLayer layer) {
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            blockTexture = textureEnabled(GL13.GL_TEXTURE0);
            auxiliaryTexture = textureEnabled(GL13.GL_TEXTURE1);
            lightmapTexture = textureEnabled(GL13.GL_TEXTURE2);
            arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            colorArray = GL11.glIsEnabled(GL11.GL_COLOR_ARRAY);
            currentColor = BufferUtils.createFloatBuffer(4);
            GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, currentColor);
            lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
            fog = GL11.glIsEnabled(GL11.GL_FOG);
            alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
            alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            blendSourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            blendDestinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            blendSourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            blendDestinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            colorMask = BufferUtils.createByteBuffer(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            dither = GL11.glIsEnabled(GL11.GL_DITHER);
            colorLogic = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
            polygonSmooth = GL11.glIsEnabled(GL11.GL_POLYGON_SMOOTH);
            multisample = GL11.glIsEnabled(GL13.GL_MULTISAMPLE);
            sampleAlphaToCoverage = GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
            framebufferSrgb = framebufferSrgbSupported()
                && GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
            textureEnvironment = layer.usesTextureCoverage()
                ? TextureEnvironmentState.capture()
                : null;
        }

        private static GlState capture(SemanticRenderLayer layer) {
            return new GlState(layer);
        }

        private void restore() {
            RenderSystem.glBindBuffer(GL15.GL_ARRAY_BUFFER, () -> arrayBuffer);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            if (textureEnvironment != null) {
                textureEnvironment.restore();
            }
            RenderSystem.alphaFunc(alphaFunction, alphaReference);
            GlStateManager.color4f(
                currentColor.get(0),
                currentColor.get(1),
                currentColor.get(2),
                currentColor.get(3)
            );
            setClientState(GL11.GL_COLOR_ARRAY, colorArray);
            setLighting(lighting);
            setFog(fog);
            setAlphaTest(alphaTest);
            setBlend(blend);
            RenderSystem.blendFuncSeparate(
                blendSourceRgb,
                blendDestinationRgb,
                blendSourceAlpha,
                blendDestinationAlpha
            );
            setDepthTest(depthTest);
            RenderSystem.depthFunc(depthFunction);
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
            setCapability(GL11.GL_DITHER, dither);
            setCapability(GL11.GL_COLOR_LOGIC_OP, colorLogic);
            setCapability(GL11.GL_POLYGON_SMOOTH, polygonSmooth);
            setCapability(GL13.GL_MULTISAMPLE, multisample);
            setCapability(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE, sampleAlphaToCoverage);
            setFramebufferSrgb(framebufferSrgb);
            GL20.glUseProgram(program);
            RenderSystem.activeTexture(activeTexture);
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

        private static void setAlphaTest(boolean enabled) {
            if (enabled) {
                RenderSystem.enableAlphaTest();
            } else {
                RenderSystem.disableAlphaTest();
            }
        }

        private static void setBlend(boolean enabled) {
            if (enabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
        }

        private static void setDepthTest(boolean enabled) {
            if (enabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
        }

        private static void setCapability(int capability, boolean enabled) {
            if (enabled) {
                GL11.glEnable(capability);
            } else {
                GL11.glDisable(capability);
            }
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
        private final int source1Alpha;
        private final int operand1Alpha;
        private final float alphaScale;

        private TextureEnvironmentState() {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            mode = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE);
            combineRgb = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB);
            source0Rgb = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB);
            operand0Rgb = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB);
            rgbScale = GL11.glGetTexEnvf(GL11.GL_TEXTURE_ENV, GL13.GL_RGB_SCALE);
            combineAlpha = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA);
            source0Alpha = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA);
            operand0Alpha = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA);
            source1Alpha = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA);
            operand1Alpha = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA);
            alphaScale = GL11.glGetTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_ALPHA_SCALE);
        }

        private static TextureEnvironmentState capture() {
            return new TextureEnvironmentState();
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
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, source1Alpha);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA, operand1Alpha);
            GL11.glTexEnvf(GL11.GL_TEXTURE_ENV, GL11.GL_ALPHA_SCALE, alphaScale);
        }
    }
}
