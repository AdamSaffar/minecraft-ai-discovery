package io.github.minecraftaidiscovery.semantic.overlay;

import io.github.minecraftaidiscovery.semantic.firstperson.FirstPersonDepthCoverageResult;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeCapture;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexFormat;

public final class CameraOverlayRetentionCapture {
    // One render-thread session bridges the terrain hook to the later hand/overlay stage.
    private static final ThreadLocal<State> PENDING = new ThreadLocal<>();

    private CameraOverlayRetentionCapture() {
    }

    public static void schedule(TranslucentProbeCapture.PreparedCapture capture) {
        if (capture == null) {
            throw new IllegalArgumentException("Prepared F9 capture is missing");
        }
        if (PENDING.get() != null) {
            throw new IllegalStateException("An F9 camera-overlay capture is already pending");
        }
        PENDING.set(new State(capture));
    }

    public static void beginOverlayStage(MinecraftClient client) {
        State state = PENDING.get();
        if (state == null || state.failure != null) {
            return;
        }
        try {
            state.tracker.beginOverlayStage();
            state.gpu = CameraOverlayRetentionGpuTransport.begin(
                client,
                TranslucentProbeCapture.WIDTH,
                TranslucentProbeCapture.HEIGHT
            );
        } catch (RuntimeException exception) {
            state.fail(exception);
        }
    }

    public static void begin(CameraOverlayKind kind) {
        State state = PENDING.get();
        if (state == null || state.failure != null) {
            return;
        }
        try {
            state.tracker.begin(kind);
        } catch (RuntimeException exception) {
            state.fail(exception);
        }
    }

    public static void mirror(
        ByteBuffer buffer,
        int mode,
        VertexFormat vertexFormat,
        int count
    ) {
        State state = PENDING.get();
        if (state == null || state.failure != null || state.gpu == null) {
            return;
        }
        try {
            CameraOverlayKind kind = state.tracker.recordDraw();
            state.gpu.mirror(kind, buffer, mode, vertexFormat, count);
        } catch (RuntimeException exception) {
            state.fail(exception);
        }
    }

    public static void end(CameraOverlayKind kind) {
        State state = PENDING.get();
        if (state == null || state.failure != null) {
            return;
        }
        try {
            state.tracker.end(kind);
        } catch (RuntimeException exception) {
            state.fail(exception);
        }
    }

    public static void endOverlayStage() {
        State state = PENDING.get();
        if (state == null || state.failure != null) {
            return;
        }
        try {
            state.tracker.endOverlayStage();
        } catch (RuntimeException exception) {
            state.fail(exception);
        }
    }

    public static Path completePending() throws IOException {
        State state = PENDING.get();
        if (state == null) {
            return null;
        }
        PENDING.remove();
        if (state.failure != null) {
            state.abortGpu();
            throw new IllegalStateException(
                "Camera-overlay retention capture failed without changing normal rendering",
                state.failure
            );
        }

        Map<CameraOverlayKind, Integer> drawCounts = state.tracker.finish();
        FirstPersonDepthCoverageResult firstPersonDepthCoverage = state.gpu == null
            ? FirstPersonDepthCoverageResult.noFirstPersonStage(
                TranslucentProbeCapture.WIDTH,
                TranslucentProbeCapture.HEIGHT
            )
            : state.gpu.getFirstPersonDepthCoverage();
        CameraOverlayRetentionResult result = state.gpu == null
            ? CameraOverlayRetentionResult.noOverlayStage(
                TranslucentProbeCapture.WIDTH,
                TranslucentProbeCapture.HEIGHT
            )
            : state.gpu.finish(state.tracker.wasOverlayStageEntered(), drawCounts);
        return state.capture.complete(result, firstPersonDepthCoverage);
    }

    static boolean hasPendingForTesting() {
        return PENDING.get() != null;
    }

    private static final class State {
        private final TranslucentProbeCapture.PreparedCapture capture;
        private final CameraOverlayRetentionTracker tracker =
            new CameraOverlayRetentionTracker();
        private CameraOverlayRetentionGpuTransport.Session gpu;
        private RuntimeException failure;

        private State(TranslucentProbeCapture.PreparedCapture capture) {
            this.capture = capture;
        }

        private void fail(RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            }
        }

        private void abortGpu() {
            if (gpu != null) {
                gpu.abort();
            }
        }
    }
}
