package io.github.minecraftaidiscovery.semantic.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

public class CameraOverlayRetentionResultTest {
    @Test
    public void noOverlayStageIsAllRetainedAndExplicitlyNotEntered() {
        CameraOverlayRetentionResult result =
            CameraOverlayRetentionResult.noOverlayStage(2, 2);

        assertArrayEquals(
            new byte[] {(byte)255, (byte)255, (byte)255, (byte)255},
            result.copyFullScreenRetention()
        );
        assertFalse(result.wasOverlayStageEntered());
        assertTrue(result.getDrawCounts().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDrawsWithoutAnEnteredStage() {
        new CameraOverlayRetentionResult(
            1,
            1,
            new byte[] {(byte)255},
            false,
            Collections.singletonMap(CameraOverlayKind.INSIDE_BLOCK, 1)
        );
    }
}
