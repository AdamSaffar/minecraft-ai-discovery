package io.github.minecraftaidiscovery.semantic.visibility;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

public final class SameFrameRgbGpuCapture {
    private SameFrameRgbGpuCapture() {
    }

    public static SameFrameRgbResult capture(MinecraftClient client) {
        if (client == null) {
            throw new IllegalArgumentException("Minecraft client is missing");
        }
        RenderSystem.assertThread(RenderSystem::isOnRenderThread);
        Framebuffer framebuffer = client.getFramebuffer();
        GlState state = GlState.capture();
        // A different active target would no longer be the prepared probe's ordinary frame.
        requireFramebufferState(
            framebuffer.viewportWidth,
            framebuffer.viewportHeight,
            framebuffer.textureWidth,
            framebuffer.textureHeight,
            framebuffer.fbo,
            state.drawFramebuffer,
            state.readFramebuffer,
            state.pixelPackBuffer,
            state.samples,
            state.viewport.get(0),
            state.viewport.get(1),
            state.viewport.get(2),
            state.viewport.get(3)
        );
        if (GlStateManager.checkFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)
            != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Same-frame RGB framebuffer is incomplete");
        }
        int attachmentType = GL30.glGetFramebufferAttachmentParameteri(
            GL30.GL_READ_FRAMEBUFFER,
            GL30.GL_COLOR_ATTACHMENT0,
            GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
        );
        int attachmentName = GL30.glGetFramebufferAttachmentParameteri(
            GL30.GL_READ_FRAMEBUFFER,
            GL30.GL_COLOR_ATTACHMENT0,
            GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
        );
        if (attachmentType != GL11.GL_TEXTURE || attachmentName != framebuffer.colorAttachment) {
            throw new IllegalStateException(
                "Same-frame RGB main color attachment is unavailable or unexpected"
            );
        }

        return runWithRestoredState(
            () -> {
                state.prepareRead(framebuffer.fbo);
                return readAndEncode(
                    readback -> GL11.glReadPixels(
                        0,
                        0,
                        SameFrameRgbEncoding.SOURCE_WIDTH,
                        SameFrameRgbEncoding.SOURCE_HEIGHT,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        readback
                    )
                );
            },
            state::restore
        );
    }

    static SameFrameRgbResult readAndEncode(Consumer<ByteBuffer> readbackOperation) {
        if (readbackOperation == null) {
            throw new IllegalArgumentException("Same-frame RGB readback operation is missing");
        }
        ByteBuffer readback = BufferUtils.createByteBuffer(
            SameFrameRgbEncoding.expectedSourceLength()
        );
        // This is the only framebuffer read in the same-frame capture path.
        readbackOperation.accept(readback);
        readback.rewind();
        byte[] bottomToTopRgba = new byte[readback.remaining()];
        readback.get(bottomToTopRgba);
        return SameFrameRgbEncoding.encodeBottomToTopRgba(
            SameFrameRgbEncoding.SOURCE_WIDTH,
            SameFrameRgbEncoding.SOURCE_HEIGHT,
            bottomToTopRgba
        );
    }

    static <T> T runWithRestoredState(Supplier<T> operation, Runnable restore) {
        try {
            return operation.get();
        } finally {
            restore.run();
        }
    }

    static void requireFramebufferState(
        int viewportWidth,
        int viewportHeight,
        int textureWidth,
        int textureHeight,
        int framebuffer,
        int drawFramebuffer,
        int readFramebuffer,
        int pixelPackBuffer,
        int samples,
        int viewportX,
        int viewportY,
        int activeViewportWidth,
        int activeViewportHeight
    ) {
        if (viewportWidth != SameFrameRgbEncoding.SOURCE_WIDTH
            || viewportHeight != SameFrameRgbEncoding.SOURCE_HEIGHT
            || textureWidth != SameFrameRgbEncoding.SOURCE_WIDTH
            || textureHeight != SameFrameRgbEncoding.SOURCE_HEIGHT) {
            throw new IllegalStateException(
                "Same-frame RGB requires an exact 1920x1080 main framebuffer"
            );
        }
        if (framebuffer <= 0
            || drawFramebuffer != framebuffer
            || readFramebuffer != framebuffer) {
            throw new IllegalStateException(
                "Same-frame RGB requires the main framebuffer to remain bound"
            );
        }
        if (pixelPackBuffer != 0 || samples != 0) {
            throw new IllegalStateException(
                "Same-frame RGB does not support packed-buffer or multisample readback"
            );
        }
        if (viewportX != 0
            || viewportY != 0
            || activeViewportWidth != SameFrameRgbEncoding.SOURCE_WIDTH
            || activeViewportHeight != SameFrameRgbEncoding.SOURCE_HEIGHT) {
            throw new IllegalStateException(
                "Same-frame RGB viewport does not match the main framebuffer"
            );
        }
    }

    private static final class GlState {
        private final int drawFramebuffer;
        private final int readFramebuffer;
        private final IntBuffer viewport;
        private final int readBuffer;
        private final int pixelPackBuffer;
        private final int samples;
        private final int packAlignment;
        private final int packRowLength;
        private final int packSkipRows;
        private final int packSkipPixels;
        private final boolean packSwapBytes;
        private final boolean packLsbFirst;

        private GlState() {
            drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            viewport = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
            pixelPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            samples = GL11.glGetInteger(GL13.GL_SAMPLES);
            packAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            packRowLength = GL11.glGetInteger(GL12.GL_PACK_ROW_LENGTH);
            packSkipRows = GL11.glGetInteger(GL12.GL_PACK_SKIP_ROWS);
            packSkipPixels = GL11.glGetInteger(GL12.GL_PACK_SKIP_PIXELS);
            packSwapBytes = GL11.glGetBoolean(GL11.GL_PACK_SWAP_BYTES);
            packLsbFirst = GL11.glGetBoolean(GL11.GL_PACK_LSB_FIRST);
        }

        private static GlState capture() {
            return new GlState();
        }

        private void prepareRead(int framebuffer) {
            GlStateManager.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GlStateManager.pixelStore(GL11.GL_PACK_ALIGNMENT, 1);
            GlStateManager.pixelStore(GL12.GL_PACK_ROW_LENGTH, 0);
            GlStateManager.pixelStore(GL12.GL_PACK_SKIP_ROWS, 0);
            GlStateManager.pixelStore(GL12.GL_PACK_SKIP_PIXELS, 0);
            GlStateManager.pixelStore(GL11.GL_PACK_SWAP_BYTES, 0);
            GlStateManager.pixelStore(GL11.GL_PACK_LSB_FIRST, 0);
        }

        private void restore() {
            GlStateManager.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL11.glReadBuffer(readBuffer);
            GlStateManager.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            RenderSystem.viewport(
                viewport.get(0),
                viewport.get(1),
                viewport.get(2),
                viewport.get(3)
            );
            GlStateManager.pixelStore(GL11.GL_PACK_ALIGNMENT, packAlignment);
            GlStateManager.pixelStore(GL12.GL_PACK_ROW_LENGTH, packRowLength);
            GlStateManager.pixelStore(GL12.GL_PACK_SKIP_ROWS, packSkipRows);
            GlStateManager.pixelStore(GL12.GL_PACK_SKIP_PIXELS, packSkipPixels);
            GlStateManager.pixelStore(GL11.GL_PACK_SWAP_BYTES, packSwapBytes ? 1 : 0);
            GlStateManager.pixelStore(GL11.GL_PACK_LSB_FIRST, packLsbFirst ? 1 : 0);
        }
    }
}
