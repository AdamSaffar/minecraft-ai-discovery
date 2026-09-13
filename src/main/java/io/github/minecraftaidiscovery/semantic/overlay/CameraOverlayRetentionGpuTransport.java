package io.github.minecraftaidiscovery.semantic.overlay;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.minecraftaidiscovery.semantic.firstperson.FirstPersonDepthCoverageEncoding;
import io.github.minecraftaidiscovery.semantic.firstperson.FirstPersonDepthCoverageResult;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

public final class CameraOverlayRetentionGpuTransport {
    private CameraOverlayRetentionGpuTransport() {
    }

    public static Session begin(MinecraftClient client, int width, int height) {
        if (client == null) {
            throw new IllegalArgumentException("Minecraft client is missing");
        }
        CameraOverlayRetentionBufferEncoding.requireDimensions(width, height);
        if (!GlStateManager.supportsGl30()) {
            throw new IllegalStateException(
                "Camera-overlay retention requires scaled GL 3.0 depth transfer"
            );
        }

        GlState state = GlState.capture();
        state.requireTargetInitializationState();
        Framebuffer target = null;
        try {
            target = new Framebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
            target.setClearColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            target.clear(MinecraftClient.IS_SYSTEM_MAC);
            // World depth was cleared just before the hand pass, so this copy isolates
            // depth-writing hand/item fragments and preserves the overlays' normal depth tests.
            target.copyDepthFrom(client.getFramebuffer());
            FirstPersonDepthCoverageResult firstPersonDepthCoverage =
                readFirstPersonDepthCoverage(target, width, height);
            return new Session(
                target,
                client.getFramebuffer().fbo,
                width,
                height,
                firstPersonDepthCoverage
            );
        } catch (RuntimeException exception) {
            if (target != null) {
                target.delete();
            }
            throw exception;
        } finally {
            state.restore();
        }
    }

    static void runWithRestoredState(Runnable operation, Runnable restore) {
        try {
            operation.run();
        } finally {
            restore.run();
        }
    }

    static boolean isSupportedClearDepth(double clearDepth) {
        return Double.compare(clearDepth, FirstPersonDepthCoverageEncoding.CLEAR_DEPTH) == 0;
    }

    private static FirstPersonDepthCoverageResult readFirstPersonDepthCoverage(
        Framebuffer target,
        int width,
        int height
    ) {
        int pixels = FirstPersonDepthCoverageEncoding.requireDimensions(width, height);
        FloatBuffer readback = BufferUtils.createFloatBuffer(pixels);
        GlStateManager.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.fbo);
        GlStateManager.pixelStore(GL11.GL_PACK_ALIGNMENT, Float.BYTES);
        GL11.glReadPixels(
            0,
            0,
            width,
            height,
            GL11.GL_DEPTH_COMPONENT,
            GL11.GL_FLOAT,
            readback
        );
        float[] bottomToTopDepth = new float[pixels];
        readback.get(bottomToTopDepth);
        return new FirstPersonDepthCoverageResult(
            width,
            height,
            FirstPersonDepthCoverageEncoding.encodeBottomToTopDepth(
                width,
                height,
                bottomToTopDepth
            ),
            true
        );
    }

    public static final class Session {
        private final Framebuffer target;
        private final int mainFramebuffer;
        private final int width;
        private final int height;
        private final FirstPersonDepthCoverageResult firstPersonDepthCoverage;
        private boolean closed;

        private Session(
            Framebuffer target,
            int mainFramebuffer,
            int width,
            int height,
            FirstPersonDepthCoverageResult firstPersonDepthCoverage
        ) {
            this.target = target;
            this.mainFramebuffer = mainFramebuffer;
            this.width = width;
            this.height = height;
            this.firstPersonDepthCoverage = firstPersonDepthCoverage;
        }

        public FirstPersonDepthCoverageResult getFirstPersonDepthCoverage() {
            return firstPersonDepthCoverage;
        }

        public void mirror(
            CameraOverlayKind kind,
            ByteBuffer buffer,
            int mode,
            VertexFormat vertexFormat,
            int count
        ) {
            requireOpen();
            requireDraw(kind, buffer, mode, vertexFormat, count);
            GlState state = GlState.capture();
            state.requireOriginalOverlayState(kind, mainFramebuffer);
            runWithRestoredState(
                () -> drawToRetentionTarget(kind, buffer, mode, vertexFormat, count),
                state::restore
            );
        }

        public CameraOverlayRetentionResult finish(
            boolean overlayStageEntered,
            Map<CameraOverlayKind, Integer> drawCounts
        ) {
            requireOpen();
            closed = true;
            GlState state = GlState.capture();
            final byte[][] readback = new byte[1][];
            try {
                readback[0] = readRetention();
            } finally {
                target.delete();
                state.restore();
            }
            return new CameraOverlayRetentionResult(
                width,
                height,
                readback[0],
                overlayStageEntered,
                drawCounts
            );
        }

        public void abort() {
            if (closed) {
                return;
            }
            closed = true;
            GlState state = GlState.capture();
            try {
                target.delete();
            } finally {
                state.restore();
            }
        }

        private void drawToRetentionTarget(
            CameraOverlayKind kind,
            ByteBuffer buffer,
            int mode,
            VertexFormat vertexFormat,
            int count
        ) {
            target.beginWrite(true);
            RenderSystem.colorMask(true, true, true, false);
            RenderSystem.enableBlend();
            if (kind.usesSourceAlphaRetention()) {
                // Zero source RGB leaves destination * (1 - renderer source alpha).
                RenderSystem.blendFuncSeparate(
                    GL11.GL_ZERO,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ZERO,
                    GL11.GL_ONE
                );
            } else {
                // The unblended inside-block draw replaces all prior RGB where it passes.
                RenderSystem.blendFuncSeparate(
                    GL11.GL_ZERO,
                    GL11.GL_ZERO,
                    GL11.GL_ZERO,
                    GL11.GL_ONE
                );
            }
            GL11.glDisable(GL11.GL_DITHER);
            GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
            GL11.glDisable(GL13.GL_MULTISAMPLE);
            GL11.glDisable(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
            setFramebufferSrgb(false);

            ByteBuffer emitted = buffer.duplicate();
            emitted.clear();
            boolean drawing = false;
            try {
                vertexFormat.startDrawing(MemoryUtil.memAddress(emitted));
                drawing = true;
                GlStateManager.drawArrays(mode, 0, count);
            } finally {
                if (drawing) {
                    vertexFormat.endDrawing();
                }
            }
        }

        private byte[] readRetention() {
            target.beginRead();
            try (NativeImage image = new NativeImage(width, height, false)) {
                image.loadFromTextureImage(0, false);
                image.mirrorVertically();
                byte[] values = new byte[Math.multiplyExact(width, height)];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int encoded = image.getPixelColor(x, y);
                        int red = NativeImage.getRed(encoded);
                        int green = NativeImage.getGreen(encoded);
                        int blue = NativeImage.getBlue(encoded);
                        if (red != green || red != blue) {
                            throw new IllegalStateException(
                                "Camera-overlay retention target is not grayscale"
                            );
                        }
                        values[y * width + x] = (byte)red;
                    }
                }
                return values;
            } finally {
                target.endRead();
            }
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("Camera-overlay retention target is closed");
            }
        }

        private static void requireDraw(
            CameraOverlayKind kind,
            ByteBuffer buffer,
            int mode,
            VertexFormat vertexFormat,
            int count
        ) {
            if (kind == null || buffer == null || vertexFormat == null) {
                throw new IllegalArgumentException("Camera-overlay draw input is missing");
            }
            if (!buffer.isDirect()) {
                throw new IllegalStateException("Camera-overlay vertex data is not direct");
            }
            if (mode != GL11.GL_QUADS
                || count != 4
                || !VertexFormats.POSITION_COLOR_TEXTURE.equals(vertexFormat)) {
                throw new IllegalStateException(
                    "Camera-overlay draw does not match the verified 1.16.1 quad format"
                );
            }
            int requiredBytes = Math.multiplyExact(count, vertexFormat.getVertexSize());
            if (buffer.capacity() < requiredBytes) {
                throw new IllegalStateException("Camera-overlay vertex packet is truncated");
            }
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
        private final int drawFramebuffer;
        private final int readFramebuffer;
        private final IntBuffer viewport;
        private final int activeTexture;
        private final int texture;
        private final boolean textureEnabled;
        private final int clientTexture;
        private final boolean positionArray;
        private final boolean colorArray;
        private final boolean textureCoordinateArray;
        private final int arrayBuffer;
        private final int program;
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
        private final boolean scissor;
        private final boolean framebufferSrgb;
        private final FloatBuffer clearColor;
        private final double clearDepth;
        private final int packAlignment;

        private GlState() {
            drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            viewport = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            clientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
            GlStateManager.clientActiveTexture(GL13.GL_TEXTURE0);
            positionArray = GL11.glIsEnabled(GL11.GL_VERTEX_ARRAY);
            colorArray = GL11.glIsEnabled(GL11.GL_COLOR_ARRAY);
            textureCoordinateArray = GL11.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY);
            GlStateManager.clientActiveTexture(clientTexture);
            arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
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
            scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            framebufferSrgb = framebufferSrgbSupported()
                && GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
            clearColor = BufferUtils.createFloatBuffer(4);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
            packAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        }

        private static GlState capture() {
            return new GlState();
        }

        private void requireOriginalOverlayState(
            CameraOverlayKind kind,
            int expectedFramebuffer
        ) {
            if (drawFramebuffer != expectedFramebuffer
                || readFramebuffer != expectedFramebuffer
                || program != 0
                || !textureEnabled
                || texture == 0
                || lighting
                || fog
                || alphaTest
                || scissor
                || framebufferSrgb
                || positionArray
                || colorArray
                || textureCoordinateArray
                || colorMask.get(0) == 0
                || colorMask.get(1) == 0
                || colorMask.get(2) == 0
                || colorMask.get(3) == 0) {
                throw new IllegalStateException(
                    "Camera-overlay renderer state is outside the verified 1.16.1 path"
                );
            }
            if (kind.usesSourceAlphaRetention()) {
                if (!blend
                    || blendSourceRgb != GL11.GL_SRC_ALPHA
                    || blendDestinationRgb != GL11.GL_ONE_MINUS_SRC_ALPHA) {
                    throw new IllegalStateException(
                        "Camera-overlay blend state does not preserve source-alpha retention"
                    );
                }
            } else if (blend) {
                throw new IllegalStateException("Inside-block overlay unexpectedly uses blending");
            }
            if (kind == CameraOverlayKind.FIRE) {
                if (depthFunction != GL11.GL_ALWAYS || depthMask) {
                    throw new IllegalStateException("Fire overlay depth state is unsupported");
                }
            } else if (depthFunction != GL11.GL_LEQUAL || !depthMask) {
                throw new IllegalStateException("Camera overlay depth state is unsupported");
            }
        }

        private void requireTargetInitializationState() {
            if (scissor) {
                throw new IllegalStateException(
                    "Camera-overlay retention does not support an active scissor test"
                );
            }
            if (!isSupportedClearDepth(clearDepth)) {
                throw new IllegalStateException(
                    "First-person depth coverage requires a 1.0 depth clear value"
                );
            }
        }

        private void restore() {
            GlStateManager.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            RenderSystem.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            RenderSystem.glBindBuffer(GL15.GL_ARRAY_BUFFER, () -> arrayBuffer);
            GL20.glUseProgram(program);
            RenderSystem.activeTexture(activeTexture);
            RenderSystem.bindTexture(texture);
            setTexture(textureEnabled);
            GlStateManager.clientActiveTexture(GL13.GL_TEXTURE0);
            setClientState(GL11.GL_VERTEX_ARRAY, positionArray);
            setClientState(GL11.GL_COLOR_ARRAY, colorArray);
            setClientState(GL11.GL_TEXTURE_COORD_ARRAY, textureCoordinateArray);
            GlStateManager.clientActiveTexture(clientTexture);
            GlStateManager.color4f(
                currentColor.get(0),
                currentColor.get(1),
                currentColor.get(2),
                currentColor.get(3)
            );
            setLighting(lighting);
            setFog(fog);
            setAlphaTest(alphaTest);
            RenderSystem.alphaFunc(alphaFunction, alphaReference);
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
            setCapability(GL11.GL_DITHER, dither);
            setCapability(GL11.GL_COLOR_LOGIC_OP, colorLogic);
            setCapability(GL11.GL_POLYGON_SMOOTH, polygonSmooth);
            setCapability(GL13.GL_MULTISAMPLE, multisample);
            setCapability(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE, sampleAlphaToCoverage);
            setFramebufferSrgb(framebufferSrgb);
            GlStateManager.clearColor(
                clearColor.get(0),
                clearColor.get(1),
                clearColor.get(2),
                clearColor.get(3)
            );
            GlStateManager.clearDepth(clearDepth);
            GlStateManager.pixelStore(GL11.GL_PACK_ALIGNMENT, packAlignment);
            RenderSystem.activeTexture(activeTexture);
        }

        private static void setClientState(int capability, boolean enabled) {
            if (enabled) {
                GlStateManager.enableClientState(capability);
            } else {
                GlStateManager.disableClientState(capability);
            }
        }

        private static void setCapability(int capability, boolean enabled) {
            if (enabled) {
                GL11.glEnable(capability);
            } else {
                GL11.glDisable(capability);
            }
        }

        private static void setTexture(boolean enabled) {
            if (enabled) {
                RenderSystem.enableTexture();
            } else {
                RenderSystem.disableTexture();
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
    }
}
