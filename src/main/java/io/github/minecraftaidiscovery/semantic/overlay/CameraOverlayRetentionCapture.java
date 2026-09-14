package io.github.minecraftaidiscovery.semantic.overlay;

import io.github.minecraftaidiscovery.semantic.firstperson.FirstPersonDepthCoverageResult;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeCapture;
import io.github.minecraftaidiscovery.semantic.visibility.SameFrameRgbCaptureBoundary;
import io.github.minecraftaidiscovery.semantic.visibility.SameFrameRgbGpuCapture;
import io.github.minecraftaidiscovery.semantic.visibility.SameFrameRgbResult;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;
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
        schedule(capture::complete);
    }

    static void scheduleForTesting(PendingCompletion completion) {
        schedule(completion);
    }

    private static void schedule(PendingCompletion completion) {
        if (completion == null) {
            throw new IllegalArgumentException("Prepared F9 completion is missing");
        }
        if (PENDING.get() != null) {
            throw new IllegalStateException("An F9 camera-overlay capture is already pending");
        }
        PENDING.set(new State(completion));
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

    public static Path completePending(SameFrameRgbCaptureBoundary boundary)
        throws IOException {
        return completePending(
            boundary,
            () -> SameFrameRgbGpuCapture.capture(MinecraftClient.getInstance())
        );
    }

    static Path completePending(
        SameFrameRgbCaptureBoundary boundary,
        Supplier<SameFrameRgbResult> sameFrameRgbCapture
    ) throws IOException {
        State state = PENDING.get();
        if (state == null) {
            return null;
        }
        PENDING.remove();
        if (boundary == null || sameFrameRgbCapture == null) {
            state.abortGpu();
            throw new IllegalArgumentException("Same-frame RGB completion input is missing");
        }
        if (state.failure != null) {
            state.abortGpu();
            throw new IllegalStateException(
                "Camera-overlay retention capture failed without changing normal rendering",
                state.failure
            );
        }

        try {
            SameFrameRgbResult sameFrameRgb = sameFrameRgbCapture.get();
            if (sameFrameRgb == null) {
                throw new IllegalStateException("Same-frame RGB readback returned no result");
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
            return state.completion.complete(
                result,
                firstPersonDepthCoverage,
                sameFrameRgb,
                boundary
            );
        } catch (IOException | RuntimeException exception) {
            state.abortGpu();
            throw exception;
        }
    }

    static boolean hasPendingForTesting() {
        return PENDING.get() != null;
    }

    @FunctionalInterface
    interface PendingCompletion {
        Path complete(
            CameraOverlayRetentionResult cameraOverlayRetention,
            FirstPersonDepthCoverageResult firstPersonDepthCoverage,
            SameFrameRgbResult sameFrameRgb,
            SameFrameRgbCaptureBoundary sameFrameRgbBoundary
        ) throws IOException;
    }

    private static final class State {
        private final PendingCompletion completion;
        private final CameraOverlayRetentionTracker tracker =
            new CameraOverlayRetentionTracker();
        private CameraOverlayRetentionGpuTransport.Session gpu;
        private RuntimeException failure;

        private State(PendingCompletion completion) {
            this.completion = completion;
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
