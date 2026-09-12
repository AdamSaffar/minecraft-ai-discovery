package io.github.minecraftaidiscovery.semantic.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;

public class CameraOverlayRetentionTrackerTest {
    @Test
    public void recordsOnlyEnteredRendererPathsAndTheirExactDrawCounts() {
        CameraOverlayRetentionTracker tracker = new CameraOverlayRetentionTracker();
        tracker.beginOverlayStage();
        tracker.begin(CameraOverlayKind.UNDERWATER);
        assertEquals(CameraOverlayKind.UNDERWATER, tracker.recordDraw());
        tracker.end(CameraOverlayKind.UNDERWATER);
        tracker.begin(CameraOverlayKind.FIRE);
        tracker.recordDraw();
        tracker.recordDraw();
        tracker.end(CameraOverlayKind.FIRE);
        tracker.endOverlayStage();

        Map<CameraOverlayKind, Integer> counts = tracker.finish();
        assertTrue(tracker.wasOverlayStageEntered());
        assertEquals(Integer.valueOf(1), counts.get(CameraOverlayKind.UNDERWATER));
        assertEquals(Integer.valueOf(2), counts.get(CameraOverlayKind.FIRE));
        assertFalse(counts.containsKey(CameraOverlayKind.INSIDE_BLOCK));
    }

    @Test
    public void acceptsAStageWithNoOverlayMethods() {
        CameraOverlayRetentionTracker tracker = new CameraOverlayRetentionTracker();
        tracker.beginOverlayStage();
        tracker.endOverlayStage();

        assertTrue(tracker.finish().isEmpty());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsUnexpectedDrawCount() {
        CameraOverlayRetentionTracker tracker = new CameraOverlayRetentionTracker();
        tracker.beginOverlayStage();
        tracker.begin(CameraOverlayKind.FIRE);
        tracker.recordDraw();
        tracker.end(CameraOverlayKind.FIRE);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDrawOutsideAClassifiedMethod() {
        CameraOverlayRetentionTracker tracker = new CameraOverlayRetentionTracker();
        tracker.beginOverlayStage();
        tracker.recordDraw();
    }
}
