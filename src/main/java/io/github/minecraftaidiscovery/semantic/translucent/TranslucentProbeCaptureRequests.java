package io.github.minecraftaidiscovery.semantic.translucent;

import java.util.concurrent.atomic.AtomicBoolean;

public final class TranslucentProbeCaptureRequests {
    private static final AtomicBoolean REQUESTED = new AtomicBoolean();

    private TranslucentProbeCaptureRequests() {
    }

    public static void request() {
        if (!TranslucentProbeConfiguration.isEnabled()) {
            throw new IllegalStateException("Translucent probe is not enabled");
        }
        REQUESTED.set(true);
    }

    public static boolean consume() {
        return TranslucentProbeConfiguration.isEnabled()
            && REQUESTED.compareAndSet(true, false);
    }
}
