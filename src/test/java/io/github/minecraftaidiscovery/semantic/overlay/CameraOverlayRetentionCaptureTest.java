package io.github.minecraftaidiscovery.semantic.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import io.github.minecraftaidiscovery.semantic.visibility.SameFrameRgbCaptureBoundary;
import io.github.minecraftaidiscovery.semantic.visibility.SameFrameRgbEncoding;
import io.github.minecraftaidiscovery.semantic.visibility.SameFrameRgbResult;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class CameraOverlayRetentionCaptureTest {
    @Test
    public void fallbackCompletionReadsOnceAndPreservesNoHandBoundary() throws IOException {
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<SameFrameRgbCaptureBoundary> completedBoundary =
            new AtomicReference<>();
        Path expected = Paths.get("metadata.json");
        CameraOverlayRetentionCapture.scheduleForTesting(
            (overlay, firstPerson, rgb, boundary) -> {
                assertFalse(overlay.wasOverlayStageEntered());
                assertFalse(firstPerson.wasPostHandDepthSampled());
                assertNotNull(rgb);
                completedBoundary.set(boundary);
                return expected;
            }
        );

        Path actual = CameraOverlayRetentionCapture.completePending(
            SameFrameRgbCaptureBoundary.RENDER_WORLD_RETURN_WITHOUT_HAND,
            () -> {
                reads.incrementAndGet();
                return sameFrameResult();
            }
        );

        assertEquals(expected, actual);
        assertEquals(1, reads.get());
        assertEquals(
            SameFrameRgbCaptureBoundary.RENDER_WORLD_RETURN_WITHOUT_HAND,
            completedBoundary.get()
        );
        assertFalse(CameraOverlayRetentionCapture.hasPendingForTesting());
    }

    @Test
    public void noPendingCaptureDoesNotReadFramebuffer() throws IOException {
        AtomicInteger reads = new AtomicInteger();

        Path result = CameraOverlayRetentionCapture.completePending(
            SameFrameRgbCaptureBoundary.RENDER_HAND_RETURN,
            () -> {
                reads.incrementAndGet();
                return sameFrameResult();
            }
        );

        assertNull(result);
        assertEquals(0, reads.get());
    }

    @Test
    public void failedReadbackClearsPendingCaptureBeforeSerialization() throws IOException {
        AtomicInteger completions = new AtomicInteger();
        CameraOverlayRetentionCapture.scheduleForTesting(
            (overlay, firstPerson, rgb, boundary) -> {
                completions.incrementAndGet();
                return Paths.get("unexpected.json");
            }
        );

        try {
            CameraOverlayRetentionCapture.completePending(
                SameFrameRgbCaptureBoundary.RENDER_HAND_RETURN,
                () -> {
                    throw new IllegalStateException("readback failed");
                }
            );
            fail("Expected same-frame readback failure");
        } catch (IllegalStateException expected) {
            assertEquals(0, completions.get());
            assertFalse(CameraOverlayRetentionCapture.hasPendingForTesting());
        }
    }

    private static SameFrameRgbResult sameFrameResult() {
        return SameFrameRgbEncoding.fromTopToBottomRgb(
            new byte[SameFrameRgbEncoding.expectedRgbLength()]
        );
    }
}
