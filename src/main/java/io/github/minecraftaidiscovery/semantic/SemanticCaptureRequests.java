package io.github.minecraftaidiscovery.semantic;

import java.util.concurrent.atomic.AtomicBoolean;

public final class SemanticCaptureRequests {
    private static final AtomicBoolean REQUESTED = new AtomicBoolean();

    private SemanticCaptureRequests() {
    }

    public static void request() {
        REQUESTED.set(true);
    }

    public static boolean consume() {
        return REQUESTED.compareAndSet(true, false);
    }
}
