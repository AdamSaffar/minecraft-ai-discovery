package io.github.minecraftaidiscovery.semantic.visibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class SameFrameRgbGpuCaptureTest {
    @Test
    public void performsExactlyOneRead() {
        AtomicInteger reads = new AtomicInteger();

        SameFrameRgbResult result = SameFrameRgbGpuCapture.readAndEncode(
            ignored -> reads.incrementAndGet()
        );

        assertEquals(1, reads.get());
        assertEquals(SameFrameRgbEncoding.expectedRgbLength(), result.copyRgb().length);
    }

    @Test
    public void restoresStateAfterSuccessfulReadback() {
        AtomicInteger restores = new AtomicInteger();

        int value = SameFrameRgbGpuCapture.runWithRestoredState(
            () -> 17,
            restores::incrementAndGet
        );

        assertEquals(17, value);
        assertEquals(1, restores.get());
    }

    @Test
    public void restoresStateWhenReadbackFails() {
        AtomicInteger restores = new AtomicInteger();
        try {
            SameFrameRgbGpuCapture.runWithRestoredState(
                () -> {
                    throw new IllegalStateException("readback failed");
                },
                restores::incrementAndGet
            );
            fail("Expected readback failure");
        } catch (IllegalStateException expected) {
            assertEquals(1, restores.get());
        }
    }

    @Test
    public void acceptsOnlyTheExactBoundMainFramebufferState() {
        SameFrameRgbGpuCapture.requireFramebufferState(
            1920,
            1080,
            1920,
            1080,
            7,
            7,
            7,
            0,
            0,
            0,
            0,
            1920,
            1080
        );
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsUnexpectedNativeDimensions() {
        requireState(1919, 1080, 0, 0, 7, 7, 1920, 1080);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsAnotherReadFramebuffer() {
        requireState(1920, 1080, 0, 0, 7, 8, 1920, 1080);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsPixelPackBuffers() {
        requireState(1920, 1080, 2, 0, 7, 7, 1920, 1080);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsMultisampleReadback() {
        requireState(1920, 1080, 0, 4, 7, 7, 1920, 1080);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsViewportMismatch() {
        requireState(1920, 1080, 0, 0, 7, 7, 640, 360);
    }

    private static void requireState(
        int width,
        int height,
        int pixelPackBuffer,
        int samples,
        int drawFramebuffer,
        int readFramebuffer,
        int activeWidth,
        int activeHeight
    ) {
        SameFrameRgbGpuCapture.requireFramebufferState(
            width,
            height,
            width,
            height,
            7,
            drawFramebuffer,
            readFramebuffer,
            pixelPackBuffer,
            samples,
            0,
            0,
            activeWidth,
            activeHeight
        );
    }
}
