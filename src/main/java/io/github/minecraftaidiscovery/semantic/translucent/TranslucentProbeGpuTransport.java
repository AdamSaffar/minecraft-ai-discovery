package io.github.minecraftaidiscovery.semantic.translucent;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class TranslucentProbeGpuTransport {
    private static final int PREVIOUS_DEPTH_TEXTURE_UNIT = GL13.GL_TEXTURE3;
    private static final float DEPTH_EPSILON = 0.0000005F;

    private static int program = -1;
    private static int modeUniform;
    private static int previousDepthUniform;
    private static int targetSizeUniform;
    private static int blockAtlasUniform;

    private TranslucentProbeGpuTransport() {
    }

    public static void draw(
        TranslucentProbeRenderPass.Context context,
        int sidecarBufferId,
        boolean backgroundOnly,
        int vertexCount,
        Matrix4f matrix,
        int drawMode
    ) {
        if (backgroundOnly) {
            return;
        }
        if (sidecarBufferId < 0) {
            throw new IllegalStateException("Vertex buffer has no translucent probe sidecar");
        }
        if (drawMode != GL11.GL_QUADS || vertexCount % 4 != 0) {
            throw new IllegalStateException("Translucent probe draw is not composed of complete quads");
        }

        context.getTarget().beginWrite(false);
        ensureProgram();
        GlState state = GlState.capture();
        try {
            configureState(context.getMode());
            bindPreviousDepth(context);
            GL20.glUseProgram(program);
            GL20.glUniform1i(modeUniform, context.getMode().getShaderCode());
            GL20.glUniform1i(blockAtlasUniform, 0);
            GL20.glUniform1i(previousDepthUniform, 3);
            GL20.glUniform2f(
                targetSizeUniform,
                context.getTarget().viewportWidth,
                context.getTarget().viewportHeight
            );

            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.glBindBuffer(GL15.GL_ARRAY_BUFFER, () -> sidecarBufferId);
            // Class bytes use the color array while positions and atlas UVs stay on Minecraft's ordinary VBO.
            GlStateManager.colorPointer(
                4,
                GL11.GL_UNSIGNED_BYTE,
                TranslucentProbeVertexData.BYTES_PER_VERTEX,
                0L
            );
            GlStateManager.enableClientState(GL11.GL_COLOR_ARRAY);

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

    private static void configureState(TranslucentProbePassMode mode) {
        RenderSystem.disableLighting();
        RenderSystem.disableFog();
        RenderSystem.disableAlphaTest();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_DITHER);
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GL11.glDisable(GL13.GL_MULTISAMPLE);
        GL11.glDisable(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
        setFramebufferSrgb(false);

        if (mode == TranslucentProbePassMode.ACCUMULATED_OPACITY) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE_MINUS_SRC_ALPHA
            );
        } else if (mode == TranslucentProbePassMode.ACCEPTED_FRAGMENT_COUNT) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        } else {
            RenderSystem.disableBlend();
        }
    }

    private static void bindPreviousDepth(TranslucentProbeRenderPass.Context context) {
        RenderSystem.activeTexture(PREVIOUS_DEPTH_TEXTURE_UNIT);
        RenderSystem.bindTexture(
            context.getMode().usesPreviousDepth() ? context.getPreviousDepthTexture() : 0
        );
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
    }

    private static synchronized void ensureProgram() {
        if (program >= 0) {
            return;
        }
        if (!GL.getCapabilities().OpenGL20) {
            throw new IllegalStateException("Translucent probe requires OpenGL 2.0 shader support");
        }

        int vertexShader = compile(GL20.GL_VERTEX_SHADER, vertexShaderSource());
        int fragmentShader = compile(GL20.GL_FRAGMENT_SHADER, fragmentShaderSource());
        int createdProgram = GL20.glCreateProgram();
        GL20.glAttachShader(createdProgram, vertexShader);
        GL20.glAttachShader(createdProgram, fragmentShader);
        GL20.glLinkProgram(createdProgram);
        if (GL20.glGetProgrami(createdProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(createdProgram);
            GL20.glDeleteProgram(createdProgram);
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            throw new IllegalStateException("Translucent probe shader link failed: " + log);
        }
        GL20.glDetachShader(createdProgram, vertexShader);
        GL20.glDetachShader(createdProgram, fragmentShader);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        int foundMode = GL20.glGetUniformLocation(createdProgram, "ProbeMode");
        int foundPreviousDepth = GL20.glGetUniformLocation(createdProgram, "PreviousDepth");
        int foundTargetSize = GL20.glGetUniformLocation(createdProgram, "TargetSize");
        int foundBlockAtlas = GL20.glGetUniformLocation(createdProgram, "BlockAtlas");
        if (foundMode < 0 || foundPreviousDepth < 0 || foundTargetSize < 0 || foundBlockAtlas < 0) {
            GL20.glDeleteProgram(createdProgram);
            throw new IllegalStateException("Translucent probe shader uniforms are incomplete");
        }

        program = createdProgram;
        modeUniform = foundMode;
        previousDepthUniform = foundPreviousDepth;
        targetSizeUniform = foundTargetSize;
        blockAtlasUniform = foundBlockAtlas;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Translucent probe shader compile failed: " + log);
        }
        return shader;
    }

    static String vertexShaderSource() {
        return "#version 120\n"
            + "varying vec4 ProbeCode;\n"
            + "void main() {\n"
            + "  gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n"
            + "  gl_TexCoord[0] = gl_MultiTexCoord0;\n"
            + "  ProbeCode = gl_Color;\n"
            + "}\n";
    }

    static String fragmentShaderSource() {
        return "#version 120\n"
            + "uniform sampler2D BlockAtlas;\n"
            + "uniform sampler2D PreviousDepth;\n"
            + "uniform vec2 TargetSize;\n"
            + "uniform int ProbeMode;\n"
            + "varying vec4 ProbeCode;\n"
            + "void main() {\n"
            + "  if (ProbeCode.b < 0.001) discard;\n"
            + "  if (ProbeMode == 4 && ProbeCode.b < 0.006) discard;\n"
            + "  vec4 atlas = texture2D(BlockAtlas, gl_TexCoord[0].xy);\n"
            // Vanilla writes depth for zero-alpha translucent texels. The probe marks this deliberate incompatibility in metadata.
            + "  if (atlas.a <= 0.0) discard;\n"
            + "  if (ProbeMode == 1) {\n"
            + "    float previous = texture2D(PreviousDepth, gl_FragCoord.xy / TargetSize).r;\n"
            + "    if (gl_FragCoord.z <= previous + " + DEPTH_EPSILON + ") discard;\n"
            + "  }\n"
            + "  if (ProbeMode == 2) {\n"
            + "    gl_FragColor = vec4(0.0, 0.0, 0.0, atlas.a);\n"
            + "  } else if (ProbeMode == 3) {\n"
            + "    gl_FragColor = vec4(1.0 / 255.0, 0.0, 0.0, 1.0 / 255.0);\n"
            + "  } else {\n"
            + "    gl_FragColor = vec4(ProbeCode.r, ProbeCode.g, ProbeCode.b, atlas.a);\n"
            + "  }\n"
            + "}\n";
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
        private final int texture3;
        private final int arrayBuffer;
        private final int program;
        private final boolean colorArray;
        private final boolean lighting;
        private final boolean fog;
        private final boolean alphaTest;
        private final boolean blend;
        private final boolean depthTest;
        private final int depthFunction;
        private final boolean depthMask;
        private final ByteBuffer colorMask;
        private final int blendSourceRgb;
        private final int blendDestinationRgb;
        private final int blendSourceAlpha;
        private final int blendDestinationAlpha;
        private final boolean dither;
        private final boolean colorLogic;
        private final boolean polygonSmooth;
        private final boolean multisample;
        private final boolean sampleAlphaToCoverage;
        private final boolean framebufferSrgb;

        private GlState(
            int activeTexture,
            int texture3,
            int arrayBuffer,
            int program,
            boolean colorArray,
            boolean lighting,
            boolean fog,
            boolean alphaTest,
            boolean blend,
            boolean depthTest,
            int depthFunction,
            boolean depthMask,
            ByteBuffer colorMask,
            int blendSourceRgb,
            int blendDestinationRgb,
            int blendSourceAlpha,
            int blendDestinationAlpha,
            boolean dither,
            boolean colorLogic,
            boolean polygonSmooth,
            boolean multisample,
            boolean sampleAlphaToCoverage,
            boolean framebufferSrgb
        ) {
            this.activeTexture = activeTexture;
            this.texture3 = texture3;
            this.arrayBuffer = arrayBuffer;
            this.program = program;
            this.colorArray = colorArray;
            this.lighting = lighting;
            this.fog = fog;
            this.alphaTest = alphaTest;
            this.blend = blend;
            this.depthTest = depthTest;
            this.depthFunction = depthFunction;
            this.depthMask = depthMask;
            this.colorMask = colorMask;
            this.blendSourceRgb = blendSourceRgb;
            this.blendDestinationRgb = blendDestinationRgb;
            this.blendSourceAlpha = blendSourceAlpha;
            this.blendDestinationAlpha = blendDestinationAlpha;
            this.dither = dither;
            this.colorLogic = colorLogic;
            this.polygonSmooth = polygonSmooth;
            this.multisample = multisample;
            this.sampleAlphaToCoverage = sampleAlphaToCoverage;
            this.framebufferSrgb = framebufferSrgb;
        }

        private static GlState capture() {
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            RenderSystem.activeTexture(PREVIOUS_DEPTH_TEXTURE_UNIT);
            int texture3 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            RenderSystem.activeTexture(activeTexture);
            ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            return new GlState(
                activeTexture,
                texture3,
                GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glIsEnabled(GL11.GL_COLOR_ARRAY),
                GL11.glIsEnabled(GL11.GL_LIGHTING),
                GL11.glIsEnabled(GL11.GL_FOG),
                GL11.glIsEnabled(GL11.GL_ALPHA_TEST),
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                colorMask,
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                GL11.glIsEnabled(GL11.GL_DITHER),
                GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP),
                GL11.glIsEnabled(GL11.GL_POLYGON_SMOOTH),
                GL11.glIsEnabled(GL13.GL_MULTISAMPLE),
                GL11.glIsEnabled(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE),
                framebufferSrgbSupported() && GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB)
            );
        }

        private void restore() {
            GL20.glUseProgram(program);
            RenderSystem.glBindBuffer(GL15.GL_ARRAY_BUFFER, () -> arrayBuffer);
            setClientState(GL11.GL_COLOR_ARRAY, colorArray);
            setLighting(lighting);
            setFog(fog);
            setAlphaTest(alphaTest);
            setBlend(blend);
            setDepthTest(depthTest);
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.depthMask(depthMask);
            RenderSystem.colorMask(
                colorMask.get(0) != 0,
                colorMask.get(1) != 0,
                colorMask.get(2) != 0,
                colorMask.get(3) != 0
            );
            RenderSystem.blendFuncSeparate(
                blendSourceRgb,
                blendDestinationRgb,
                blendSourceAlpha,
                blendDestinationAlpha
            );
            setCapability(GL11.GL_DITHER, dither);
            setCapability(GL11.GL_COLOR_LOGIC_OP, colorLogic);
            setCapability(GL11.GL_POLYGON_SMOOTH, polygonSmooth);
            setCapability(GL13.GL_MULTISAMPLE, multisample);
            setCapability(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE, sampleAlphaToCoverage);
            setFramebufferSrgb(framebufferSrgb);
            RenderSystem.activeTexture(PREVIOUS_DEPTH_TEXTURE_UNIT);
            RenderSystem.bindTexture(texture3);
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
}
