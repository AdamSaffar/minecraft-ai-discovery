package io.github.minecraftaidiscovery.semantic.overlay;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class CameraOverlayRetentionGpuTransportTest {
    @Test
    public void restoresStateAfterSuccessfulMirrorWork() {
        AtomicBoolean restored = new AtomicBoolean();

        CameraOverlayRetentionGpuTransport.runWithRestoredState(
            () -> { },
            () -> restored.set(true)
        );

        assertTrue(restored.get());
    }

    @Test
    public void restoresStateAfterMirrorFailure() {
        AtomicBoolean restored = new AtomicBoolean();
        try {
            CameraOverlayRetentionGpuTransport.runWithRestoredState(
                () -> {
                    throw new IllegalStateException("draw failed");
                },
                () -> restored.set(true)
            );
            fail("Expected mirror failure");
        } catch (IllegalStateException expected) {
            assertTrue(restored.get());
        }
    }
}
